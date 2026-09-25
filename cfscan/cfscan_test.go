package cfscan

import (
	"context"
	"encoding/json"
	"errors"
	"math/rand/v2"
	"net"
	"net/http"
	"net/http/httptest"
	"net/netip"
	"os"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

func TestVersionIsReportedForTheAboutScreen(t *testing.T) {
	if Version() == "" {
		t.Fatal("Version() must not be empty")
	}
}

func TestConfigNeedsTheCustomersDomain(t *testing.T) {
	if _, err := parseConfig(`{}`); err == nil {
		t.Fatal("a config without sni must be refused")
	}
	conf, err := parseConfig(`{"sni":"cdn.example.com","maxIps":100000,"workers":0}`)
	if err != nil {
		t.Fatal(err)
	}
	if conf.Host != "cdn.example.com" {
		t.Errorf("host defaults to the sni, got %q", conf.Host)
	}
	if conf.MaxIPs != 5000 || conf.Workers != 1 {
		t.Errorf("limits not clamped: maxIps=%d workers=%d", conf.MaxIPs, conf.Workers)
	}
	if conf.Path != "/cdn-cgi/trace" || conf.Fingerprint != "chrome" || !conf.Jitter.Enable || conf.Download.Enable {
		t.Errorf("defaults changed: %+v", conf)
	}
	if _, err := parseConfig(`{"sni":"a.b","fingerprint":"netscape"}`); err == nil {
		t.Error("an unknown fingerprint must be refused")
	}
}

func TestRangesSkipCommentsAndIPv6(t *testing.T) {
	prefixes, err := parseRanges("# cloudflare\n104.16.0.0/24\n\n2606:4700::/32\n172.64.1.7/24\n")
	if err != nil {
		t.Fatal(err)
	}
	want := []string{"104.16.0.0/24", "172.64.1.0/24"}
	if len(prefixes) != len(want) {
		t.Fatalf("got %v", prefixes)
	}
	for i, p := range prefixes {
		if p.String() != want[i] {
			t.Errorf("prefix %d = %s, want %s", i, p, want[i])
		}
	}
	if _, err := parseRanges("not a range"); err == nil {
		t.Error("garbage must be refused")
	}
}

func TestSampleIsDistinctInsideTheRangesAndBounded(t *testing.T) {
	prefixes, _ := parseRanges("104.16.0.0/24\n172.64.0.0/16\n")
	rng := rand.New(rand.NewPCG(1, 2))
	addrs := sample(prefixes, 300, rng)
	if len(addrs) != 300 {
		t.Fatalf("got %d addresses", len(addrs))
	}
	seen := map[netip.Addr]bool{}
	for _, a := range addrs {
		if seen[a] {
			t.Fatalf("duplicate %s", a)
		}
		seen[a] = true
		inside := false
		for _, p := range prefixes {
			inside = inside || p.Contains(a)
		}
		if !inside {
			t.Fatalf("%s is outside the ranges", a)
		}
		if last := a.As4()[3]; last == 0 || last == 255 {
			t.Fatalf("%s is a network or broadcast address", a)
		}
	}
	small, _ := parseRanges("10.0.0.0/30\n")
	if got := sample(small, 50, rng); len(got) != 3 {
		t.Fatalf("10.0.0.0/30 has three addresses that are not .0 or .255, got %v", got)
	}
}

func TestJitterIsTheMeanAbsoluteDeviation(t *testing.T) {
	if got := jitter([]float64{100, 110, 90, 100}); got != 5 {
		t.Fatalf("jitter = %v", got)
	}
	if jitter(nil) != 0 {
		t.Fatal("no samples is no jitter")
	}
}

func TestTraceFieldReadsColo(t *testing.T) {
	body := "fl=123\nh=cdn.example.com\ncolo=FRA\nhttp=http/2\n"
	if got := traceField(body, "colo"); got != "FRA" {
		t.Fatalf("colo = %q", got)
	}
}

// -- scanning against a local TLS server -------------------------------------

type recorder struct {
	mu       sync.Mutex
	results  []scanResult
	progress int64
	done     chan string
}

func newRecorder() *recorder { return &recorder{done: make(chan string, 1)} }

func (r *recorder) OnResult(s string) {
	var res scanResult
	if err := json.Unmarshal([]byte(s), &res); err != nil {
		panic(err)
	}
	r.mu.Lock()
	r.results = append(r.results, res)
	r.mu.Unlock()
}
func (r *recorder) OnProgress(tested, _, _ int64) { atomic.StoreInt64(&r.progress, tested) }
func (r *recorder) OnFinish(message string)       { r.done <- message }

func (r *recorder) wait(t *testing.T) string {
	t.Helper()
	select {
	case m := <-r.done:
		return m
	case <-time.After(20 * time.Second):
		t.Fatal("scan did not finish")
		return ""
	}
}

// edge serves what a Cloudflare edge would for the customer's domain.
func edge(t *testing.T, server string) *httptest.Server {
	t.Helper()
	srv := httptest.NewUnstartedServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Host != "cdn.example.com" {
			http.Error(w, "wrong host", http.StatusBadRequest)
			return
		}
		w.Header().Set("Server", server)
		_, _ = w.Write([]byte("h=cdn.example.com\ncolo=TST\n"))
	}))
	srv.EnableHTTP2 = true
	srv.StartTLS()
	t.Cleanup(srv.Close)
	return srv
}

func testerFor(t *testing.T, srv *httptest.Server, conf string) *tester {
	t.Helper()
	c, err := parseConfig(conf)
	if err != nil {
		t.Fatal(err)
	}
	c.Insecure = true
	tr := newTester(c)
	target := srv.Listener.Addr().String()
	tr.dial = func(ctx context.Context, _ string) (net.Conn, error) {
		var d net.Dialer
		return d.DialContext(ctx, "tcp", target)
	}
	tr.pinger = func(netip.Addr, time.Duration) (time.Duration, error) { return 12 * time.Millisecond, nil }
	return tr
}

func addrs(n int) []netip.Addr {
	prefixes, _ := parseRanges("104.16.0.0/16\n")
	return sample(prefixes, n, rand.New(rand.NewPCG(3, 4)))
}

const fastConf = `{"sni":"cdn.example.com","stopAfter":3,"workers":4,"jitter":{"enable":true,"samples":2,"intervalMs":1,"maxMs":1000}}`

func TestAScanReportsCleanAddressesAndStopsAtTheLimit(t *testing.T) {
	srv := edge(t, "cloudflare")
	tr := testerFor(t, srv, fastConf)
	rec := newRecorder()

	go func() {
		err := scan(context.Background(), tr, addrs(40), rec)
		msg := ""
		if err != nil {
			msg = err.Error()
		}
		rec.OnFinish(msg)
	}()
	if msg := rec.wait(t); msg != "" {
		t.Fatalf("finished with %q", msg)
	}
	if len(rec.results) != 3 {
		t.Fatalf("want exactly stopAfter=3 results, got %d", len(rec.results))
	}
	r := rec.results[0]
	if r.Colo != "TST" || r.PingMs != 12 || r.Port != 443 || r.LatencyMs < 0 {
		t.Fatalf("unexpected result %+v", r)
	}
	if atomic.LoadInt64(&rec.progress) >= 40 {
		t.Fatalf("the scan should stop early, tested %d", rec.progress)
	}
}

func TestAnAnswerNotFromCloudflareIsNotClean(t *testing.T) {
	srv := edge(t, "nginx")
	tr := testerFor(t, srv, fastConf)
	rec := newRecorder()
	err := scan(context.Background(), tr, addrs(10), rec)
	if err != nil {
		t.Fatal(err)
	}
	if len(rec.results) != 0 {
		t.Fatalf("got %v", rec.results)
	}
}

func TestPingIsDroppedWhenItCannotRun(t *testing.T) {
	srv := edge(t, "cloudflare")
	tr := testerFor(t, srv, fastConf)
	tr.pinger = func(netip.Addr, time.Duration) (time.Duration, error) {
		return 0, errPingUnavailable
	}
	rec := newRecorder()
	if err := scan(context.Background(), tr, addrs(10), rec); err != nil {
		t.Fatal(err)
	}
	if len(rec.results) != 3 || !tr.ping.disabled.Load() {
		t.Fatalf("results=%d disabled=%v", len(rec.results), tr.ping.disabled.Load())
	}
}

func TestSilentPingDoesNotFilterUntilSomethingAnswers(t *testing.T) {
	srv := edge(t, "cloudflare")
	tr := testerFor(t, srv, fastConf)
	tr.pinger = func(netip.Addr, time.Duration) (time.Duration, error) { return 0, errors.New("no reply") }
	rec := newRecorder()
	if err := scan(context.Background(), tr, addrs(10), rec); err != nil {
		t.Fatal(err)
	}
	if len(rec.results) != 3 {
		t.Fatalf("a network that drops ICMP must still find addresses, got %d", len(rec.results))
	}
}

func TestStopCancelsTheScan(t *testing.T) {
	s := NewScanner()
	rec := newRecorder()
	// Unroutable documentation range: every dial waits for its timeout.
	conf := `{"sni":"cdn.example.com","maxIps":200,"workers":2,"tcpTimeoutMs":2000,"ping":{"enable":false}}`
	if err := s.Start(conf, "192.0.2.0/24\n", rec); err != nil {
		t.Fatal(err)
	}
	if !s.IsRunning() {
		t.Fatal("scan should be running")
	}
	if err := s.Start(conf, "192.0.2.0/24\n", rec); !errors.Is(err, errRunning) {
		t.Fatalf("a second start must be refused, got %v", err)
	}
	s.Stop()
	if msg := rec.wait(t); msg != "" {
		t.Fatalf("a stopped scan finishes quietly, got %q", msg)
	}
	if s.IsRunning() {
		t.Fatal("scan should have stopped")
	}
}

func TestStartRefusesBadInput(t *testing.T) {
	s := NewScanner()
	if err := s.Start(`{}`, "104.16.0.0/24", newRecorder()); err == nil || !strings.Contains(err.Error(), "sni") {
		t.Fatalf("got %v", err)
	}
	if err := s.Start(`{"sni":"a.b"}`, "", newRecorder()); err == nil {
		t.Fatal("empty ranges must be refused")
	}
}

func TestKnownAddressesAreRetestedFirst(t *testing.T) {
	prefixes, _ := parseRanges("104.16.0.0/16\n")
	sampled := sample(prefixes, 5, rand.New(rand.NewPCG(5, 6)))
	got := withPreferred([]string{"1.1.1.1", "bogus", "::1", sampled[0].String()}, sampled, 5)
	if len(got) != 5 || got[0].String() != "1.1.1.1" || got[1] != sampled[0] {
		t.Fatalf("got %v", got)
	}
}

func TestTheBundledRangesParse(t *testing.T) {
	raw, err := os.ReadFile("../V2rayNG/app/src/main/assets/cfscan/ipv4.txt")
	if err != nil {
		t.Skip("app assets not present:", err)
	}
	prefixes, err := parseRanges(string(raw))
	if err != nil {
		t.Fatal(err)
	}
	if len(prefixes) < 1000 {
		t.Fatalf("only %d ranges", len(prefixes))
	}
	if got := sample(prefixes, 300, rand.New(rand.NewPCG(7, 8))); len(got) != 300 {
		t.Fatalf("sampled %d", len(got))
	}
}
