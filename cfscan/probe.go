package cfscan

import (
	"bufio"
	"context"
	"crypto/tls"
	"errors"
	"fmt"
	"io"
	"math"
	"math/rand/v2"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"slices"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	probing "github.com/prometheus-community/pro-bing"
	utls "github.com/refraction-networking/utls"
	"golang.org/x/net/http2"
)

func helloFor(name string) (utls.ClientHelloID, bool) {
	switch name {
	case "chrome":
		return utls.HelloChrome_Auto, true
	case "firefox":
		return utls.HelloFirefox_Auto, true
	case "edge":
		return utls.HelloEdge_Auto, true
	case "ios":
		return utls.HelloIOS_Auto, true
	case "360":
		return utls.Hello360_Auto, true
	}
	return utls.ClientHelloID{}, false
}

// pingState decides whether ping still filters anything. Unprivileged ICMP
// is refused on some Android builds and some networks drop all of it; then
// every address would fail the ping and the scan would find nothing. Ping
// only counts as a filter once something has answered, and is switched off
// for the rest of the scan when it cannot run at all or when nothing has
// answered after pingProbeWindow tries.
type pingState struct {
	disabled atomic.Bool
	answered atomic.Bool
	tries    atomic.Int64
}

const pingProbeWindow = 30

type tester struct {
	conf  config
	hello utls.ClientHelloID
	ping  pingState
	// dial is replaceable in tests; the app always dials the real address.
	dial func(ctx context.Context, addr string) (net.Conn, error)
	// pinger is replaceable in tests. It returns the round trip, or an error.
	pinger func(ip netip.Addr, timeout time.Duration) (time.Duration, error)
}

func newTester(conf config) *tester {
	hello, _ := helloFor(conf.Fingerprint)
	t := &tester{conf: conf, hello: hello}
	dialer := &net.Dialer{Timeout: time.Duration(conf.TCPTimeoutMs) * time.Millisecond}
	t.dial = func(ctx context.Context, addr string) (net.Conn, error) {
		return dialer.DialContext(ctx, "tcp", addr)
	}
	t.pinger = icmpPing
	return t
}

func run(ctx context.Context, conf config, prefixes []netip.Prefix, listener Listener) error {
	seed := conf.Seed
	if seed == 0 {
		seed = rand.Uint64()
	}
	sampled := sample(prefixes, conf.MaxIPs, rand.New(rand.NewPCG(seed, seed^0x9e3779b97f4a7c15)))
	return scan(ctx, newTester(conf), withPreferred(conf.PreferIPs, sampled, conf.MaxIPs), listener)
}

// withPreferred puts the known addresses first and fills the rest of the
// budget from the sample, without testing any address twice.
func withPreferred(preferred []string, sampled []netip.Addr, limit int) []netip.Addr {
	out := make([]netip.Addr, 0, limit)
	seen := make(map[netip.Addr]struct{}, limit)
	add := func(a netip.Addr) {
		if len(out) >= limit {
			return
		}
		if _, dup := seen[a]; dup {
			return
		}
		seen[a] = struct{}{}
		out = append(out, a)
	}
	for _, s := range preferred {
		if a, err := netip.ParseAddr(s); err == nil && a.Is4() {
			add(a)
		}
	}
	for _, a := range sampled {
		add(a)
	}
	return out
}

func scan(ctx context.Context, t *tester, addrs []netip.Addr, listener Listener) error {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	total := int64(len(addrs))
	var tested, found atomic.Int64
	jobs := make(chan netip.Addr)
	var wg sync.WaitGroup
	var reportMu sync.Mutex

	for range t.conf.Workers {
		wg.Go(func() {
			for addr := range jobs {
				result, ok := t.test(ctx, addr)
				n := tested.Add(1)
				reportMu.Lock()
				if ok && ctx.Err() == nil && found.Load() < int64(t.conf.StopAfter) {
					f := found.Add(1)
					listener.OnResult(result.json())
					if f >= int64(t.conf.StopAfter) {
						cancel()
					}
				}
				listener.OnProgress(n, total, found.Load())
				reportMu.Unlock()
			}
		})
	}

	listener.OnProgress(0, total, 0)
feed:
	for _, addr := range addrs {
		select {
		case <-ctx.Done():
			break feed
		case jobs <- addr:
		}
	}
	close(jobs)
	wg.Wait()
	// A scan stopped because enough was found is a complete scan.
	if found.Load() >= int64(t.conf.StopAfter) {
		return nil
	}
	return ctx.Err()
}

// test runs every check on one address; ok is false as soon as one fails.
func (t *tester) test(ctx context.Context, ip netip.Addr) (scanResult, bool) {
	if ctx.Err() != nil {
		return scanResult{}, false
	}
	result := scanResult{IP: ip.String(), Port: t.conf.Port}

	if t.conf.Ping.Enable && !t.ping.disabled.Load() {
		rtt, err := t.pinger(ip, time.Duration(t.conf.Ping.MaxMs)*time.Millisecond)
		tries := t.ping.tries.Add(1)
		switch {
		case err == nil:
			t.ping.answered.Store(true)
			result.PingMs = rtt.Milliseconds()
		case errors.Is(err, errPingUnavailable):
			t.ping.disabled.Store(true)
		case t.ping.answered.Load():
			// Ping works on this network and this address did not answer.
			return scanResult{}, false
		case tries >= pingProbeWindow:
			t.ping.disabled.Store(true)
		}
	}

	client, conn, err := t.connect(ctx, ip, t.conf.SNI)
	if err != nil {
		return scanResult{}, false
	}
	defer conn.Close()
	defer client.CloseIdleConnections()

	started := time.Now()
	body, err := t.get(ctx, client, ip, t.conf.Host, t.conf.Path)
	if err != nil {
		return scanResult{}, false
	}
	result.LatencyMs = time.Since(started).Milliseconds()
	result.Colo = traceField(body, "colo")

	if t.conf.Jitter.Enable {
		samples := make([]float64, 0, t.conf.Jitter.Samples)
		for range t.conf.Jitter.Samples {
			if !sleep(ctx, time.Duration(t.conf.Jitter.IntervalMs)*time.Millisecond) {
				return scanResult{}, false
			}
			s := time.Now()
			if _, err := t.get(ctx, client, ip, t.conf.Host, t.conf.Path); err != nil {
				// cf-scanner calls this JAMMED: it answered once, then stalled.
				return scanResult{}, false
			}
			samples = append(samples, float64(time.Since(s).Milliseconds()))
		}
		result.JitterMs = math.Round(jitter(samples)*10) / 10
		if result.JitterMs > t.conf.Jitter.MaxMs {
			return scanResult{}, false
		}
	}

	if t.conf.Download.Enable {
		result.DownloadKBps = t.download(ctx, ip)
	}
	return result, true
}

// connect opens TCP and a uTLS session with the browser fingerprint, and an
// HTTP client that only ever uses that one connection.
func (t *tester) connect(ctx context.Context, ip netip.Addr, sni string) (*http.Client, net.Conn, error) {
	addr := netip.AddrPortFrom(ip, uint16(t.conf.Port)).String()
	raw, err := t.dial(ctx, addr)
	if err != nil {
		return nil, nil, err
	}
	uconn := utls.UClient(raw, &utls.Config{ServerName: sni, InsecureSkipVerify: t.conf.Insecure}, t.hello)
	hctx, cancel := context.WithTimeout(ctx, time.Duration(t.conf.MaxLatencyMs)*time.Millisecond)
	defer cancel()
	if err := uconn.HandshakeContext(hctx); err != nil {
		_ = raw.Close()
		return nil, nil, fmt.Errorf("%s: tls: %w", addr, err)
	}
	var transport http.RoundTripper
	if uconn.ConnectionState().NegotiatedProtocol == "h2" {
		transport = &http2.Transport{
			DialTLSContext: func(context.Context, string, string, *tls.Config) (net.Conn, error) { return uconn, nil },
		}
	} else {
		transport = &http.Transport{
			DialTLSContext:    func(context.Context, string, string) (net.Conn, error) { return uconn, nil },
			DisableKeepAlives: false,
		}
	}
	client := &http.Client{
		Transport: transport,
		Timeout:   time.Duration(t.conf.MaxLatencyMs) * time.Millisecond,
		// A redirect would leave the address under test.
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
	}
	return client, uconn, nil
}

func (t *tester) get(ctx context.Context, client *http.Client, ip netip.Addr, host, path string) (string, error) {
	u := &url.URL{Scheme: "https", Host: netip.AddrPortFrom(ip, uint16(t.conf.Port)).String(), Path: path}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u.String(), nil)
	if err != nil {
		return "", err
	}
	req.Host = host
	req.Header.Set("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36")
	resp, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
	if len(t.conf.StatusCodes) > 0 && !slices.Contains(t.conf.StatusCodes, resp.StatusCode) {
		return "", fmt.Errorf("status %d", resp.StatusCode)
	}
	if t.conf.ServerHeader != "" && !strings.EqualFold(resp.Header.Get("Server"), t.conf.ServerHeader) {
		return "", fmt.Errorf("server %q", resp.Header.Get("Server"))
	}
	return string(body), nil
}

// download fetches up to TargetBytes through the address, on a connection of
// its own (the download host has its own SNI), and returns KB/s; 0 on failure.
func (t *tester) download(ctx context.Context, ip netip.Addr) int64 {
	target, err := url.Parse(t.conf.Download.URL)
	if err != nil || target.Hostname() == "" {
		return 0
	}
	dctx, cancel := context.WithTimeout(ctx, time.Duration(t.conf.Download.TimeoutMs)*time.Millisecond)
	defer cancel()
	client, conn, err := t.connect(dctx, ip, target.Hostname())
	if err != nil {
		return 0
	}
	defer conn.Close()
	client.Timeout = 0 // dctx bounds it
	u := &url.URL{Scheme: "https", Host: netip.AddrPortFrom(ip, uint16(t.conf.Port)).String(), Path: target.Path, RawQuery: target.RawQuery}
	req, err := http.NewRequestWithContext(dctx, http.MethodGet, u.String(), nil)
	if err != nil {
		return 0
	}
	req.Host = target.Host
	started := time.Now()
	resp, err := client.Do(req)
	if err != nil {
		return 0
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return 0
	}
	read, _ := io.CopyN(io.Discard, resp.Body, int64(t.conf.Download.TargetBytes))
	elapsed := time.Since(started).Seconds()
	if read < int64(t.conf.Download.TargetBytes) || elapsed <= 0 {
		return 0
	}
	return int64(float64(read) / 1000 / elapsed)
}

// Jitter is cf-scanner's: the mean absolute deviation of the samples.
func jitter(samples []float64) float64 {
	if len(samples) == 0 {
		return 0
	}
	var sum float64
	for _, s := range samples {
		sum += s
	}
	mean := sum / float64(len(samples))
	var dev float64
	for _, s := range samples {
		dev += math.Abs(s - mean)
	}
	return dev / float64(len(samples))
}

// traceField reads one key=value line of a /cdn-cgi/trace body.
func traceField(body, key string) string {
	scanner := bufio.NewScanner(strings.NewReader(body))
	for scanner.Scan() {
		k, v, ok := strings.Cut(scanner.Text(), "=")
		if ok && k == key {
			return strings.TrimSpace(v)
		}
	}
	return ""
}

var errPingUnavailable = errors.New("ping unavailable")

// icmpPing sends one unprivileged ICMP echo (a datagram socket, which Android
// allows apps where the kernel's ping_group_range does).
func icmpPing(ip netip.Addr, timeout time.Duration) (time.Duration, error) {
	pinger, err := probing.NewPinger(ip.String())
	if err != nil {
		return 0, err
	}
	pinger.SetPrivileged(false)
	pinger.Count = 1
	pinger.Timeout = timeout
	if err := pinger.Run(); err != nil {
		// Socket creation refused: nothing will ever answer on this device.
		return 0, fmt.Errorf("%w: %v", errPingUnavailable, err)
	}
	stats := pinger.Statistics()
	if stats.PacketsRecv == 0 {
		return 0, errors.New("no reply")
	}
	return stats.AvgRtt, nil
}

func sleep(ctx context.Context, d time.Duration) bool {
	if d <= 0 {
		return ctx.Err() == nil
	}
	timer := time.NewTimer(d)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return false
	case <-timer.C:
		return true
	}
}
