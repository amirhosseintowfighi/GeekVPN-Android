// Package cfscan is GeekVPN's Cloudflare clean-IP scanner.
//
// It is compiled into libv2ray.aar by the same gomobile bind as the Xray core
// (scripts/build-libv2ray.sh). Two gomobile AARs cannot share one app: each
// carries its own Go runtime and its own go.* Java classes, and the second one
// to load fails. Binding both packages together gives one runtime and one set
// of glue classes, with Kotlin reaching this package as cfscan.Cfscan.
//
// The probing follows radioactiveAHM/cf-scanner (MIT): ping, TCP, uTLS with a
// browser fingerprint, an HTTP request that must come back from Cloudflare,
// jitter over a few more requests, and an optional download test. What differs
// is the shape: a library with a start/stop API and callbacks instead of a CLI,
// a bounded random sample of addresses instead of every one, and the customer's
// own domain as SNI and Host, since the address has to work for that domain.
package cfscan

import (
	"context"
	"encoding/json"
	"errors"
	"sync"
)

// version is bumped whenever the scanner's behaviour changes, so a bug report
// from the About screen says which engine produced it.
const version = "1.0.0"

// Version identifies the scanner engine built into this AAR.
func Version() string {
	return version
}

// Listener receives a scan's events. gomobile turns it into a Java interface;
// every method is called from a Go thread, never the Android main thread.
type Listener interface {
	// OnResult reports one address that passed every check, as result JSON.
	OnResult(resultJSON string)
	// OnProgress reports how many addresses were tried of how many, and how
	// many passed so far.
	OnProgress(tested int64, total int64, found int64)
	// OnFinish ends the scan: an empty message when it ran to completion or was
	// stopped, else why it could not run.
	OnFinish(errorMessage string)
}

// Scanner runs one scan at a time. It is safe to call from any thread.
type Scanner struct {
	mu      sync.Mutex
	cancel  context.CancelFunc
	running bool
}

// NewScanner makes an idle scanner.
func NewScanner() *Scanner {
	return &Scanner{}
}

// errRunning is returned by Start while a scan is already in progress.
var errRunning = errors.New("a scan is already running")

// Start begins a scan in the background and returns at once. configJSON is a
// config; ranges is the CIDR list, one per line (the bundled ipv4.txt).
func (s *Scanner) Start(configJSON string, ranges string, listener Listener) error {
	if listener == nil {
		return errors.New("listener is required")
	}
	conf, err := parseConfig(configJSON)
	if err != nil {
		return err
	}
	prefixes, err := parseRanges(ranges)
	if err != nil {
		return err
	}

	s.mu.Lock()
	if s.running {
		s.mu.Unlock()
		return errRunning
	}
	ctx, cancel := context.WithCancel(context.Background())
	s.cancel = cancel
	s.running = true
	s.mu.Unlock()

	go func() {
		defer func() {
			s.mu.Lock()
			s.running = false
			s.cancel = nil
			s.mu.Unlock()
			cancel()
		}()
		runErr := run(ctx, conf, prefixes, listener)
		message := ""
		if runErr != nil && !errors.Is(runErr, context.Canceled) {
			message = runErr.Error()
		}
		listener.OnFinish(message)
	}()
	return nil
}

// Stop cancels the running scan, if any. OnFinish still follows.
func (s *Scanner) Stop() {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.cancel != nil {
		s.cancel()
	}
}

// IsRunning reports whether a scan is in progress.
func (s *Scanner) IsRunning() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.running
}

// result is one clean address, as OnResult reports it.
type scanResult struct {
	IP        string  `json:"ip"`
	Port      int     `json:"port"`
	PingMs    int64   `json:"pingMs"`
	LatencyMs int64   `json:"latencyMs"`
	JitterMs  float64 `json:"jitterMs"`
	// DownloadKBps is 0 when the download test was off or failed.
	DownloadKBps int64 `json:"downloadKBps"`
	// Colo is Cloudflare's data centre code from /cdn-cgi/trace ("FRA").
	Colo string `json:"colo"`
}

func (r scanResult) json() string {
	b, _ := json.Marshal(r)
	return string(b)
}
