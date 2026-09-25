package cfscan

import (
	"os"
	"testing"
	"time"
)

// TestScanningRealCloudflareFindsAnAddress runs the whole engine against the
// real Cloudflare edge: sampling the bundled ranges, TCP, uTLS with the Chrome
// fingerprint, /cdn-cgi/trace and jitter. Only where CFSCAN_LIVE is set (CI),
// since it needs the internet and takes a few seconds.
func TestScanningRealCloudflareFindsAnAddress(t *testing.T) {
	if os.Getenv("CFSCAN_LIVE") == "" {
		t.Skip("set CFSCAN_LIVE=1 to scan the real Cloudflare edge")
	}
	raw, err := os.ReadFile("../V2rayNG/app/src/main/assets/cfscan/ipv4.txt")
	if err != nil {
		t.Fatal(err)
	}
	rec := newRecorder()
	s := NewScanner()
	conf := `{"sni":"speed.cloudflare.com","maxIps":80,"stopAfter":2,"workers":16,"jitter":{"enable":true,"samples":3,"intervalMs":100,"maxMs":400}}`
	if err := s.Start(conf, string(raw), rec); err != nil {
		t.Fatal(err)
	}
	select {
	case msg := <-rec.done:
		if msg != "" {
			t.Fatalf("scan failed: %s", msg)
		}
	case <-time.After(90 * time.Second):
		s.Stop()
		t.Fatal("scan did not finish in 90s")
	}
	if len(rec.results) == 0 {
		t.Fatal("no clean address among 80 Cloudflare addresses")
	}
	r := rec.results[0]
	if r.Colo == "" || r.LatencyMs <= 0 {
		t.Fatalf("result lacks the trace data: %+v", r)
	}
	t.Logf("found %d, best %+v", len(rec.results), r)
}
