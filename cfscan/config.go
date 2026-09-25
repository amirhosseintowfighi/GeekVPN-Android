package cfscan

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"
)

// config is what the app sends to Start. Every field has a default, so {}
// with an SNI is a valid scan.
type config struct {
	// SNI and Host are the customer's own CDN domain: an address is only
	// clean if Cloudflare serves that domain on it.
	SNI  string `json:"sni"`
	Host string `json:"host"`
	// Path is requested on each address. /cdn-cgi/trace is answered by the
	// Cloudflare edge for any proxied domain without reaching the origin.
	Path string `json:"path"`
	Port int    `json:"port"`

	// MaxIPs bounds a scan, for battery and data; StopAfter ends it early once
	// that many clean addresses are found.
	MaxIPs       int `json:"maxIps"`
	StopAfter    int `json:"stopAfter"`
	Workers      int `json:"workers"`
	MaxLatencyMs int `json:"maxLatencyMs"`
	TCPTimeoutMs int `json:"tcpTimeoutMs"`

	// Fingerprint is the uTLS browser hello: chrome, firefox, edge, ios, 360.
	Fingerprint  string `json:"fingerprint"`
	ServerHeader string `json:"serverHeader"`
	StatusCodes  []int  `json:"statusCodes"`

	Ping     pingConfig     `json:"ping"`
	Jitter   jitterConfig   `json:"jitter"`
	Download downloadConfig `json:"download"`

	// PreferIPs are tried first, before the random sample: the addresses an
	// earlier scan found, re-tested once they have gone stale.
	PreferIPs []string `json:"preferIps"`

	// Seed fixes the address sample (tests); 0 is random.
	Seed uint64 `json:"seed"`
	// Insecure skips certificate checks. Tests only; the app never sets it.
	Insecure bool `json:"insecure"`
}

type pingConfig struct {
	Enable bool `json:"enable"`
	MaxMs  int  `json:"maxMs"`
}

type jitterConfig struct {
	Enable     bool    `json:"enable"`
	MaxMs      float64 `json:"maxMs"`
	Samples    int     `json:"samples"`
	IntervalMs int     `json:"intervalMs"`
}

type downloadConfig struct {
	Enable      bool   `json:"enable"`
	URL         string `json:"url"`
	TargetBytes int    `json:"targetBytes"`
	TimeoutMs   int    `json:"timeoutMs"`
}

// Defaults follow cf-scanner's conf.json, with the bounds a phone needs.
func defaults() config {
	return config{
		Path:         "/cdn-cgi/trace",
		Port:         443,
		MaxIPs:       300,
		StopAfter:    5,
		Workers:      16,
		MaxLatencyMs: 2000,
		TCPTimeoutMs: 1000,
		Fingerprint:  "chrome",
		ServerHeader: "cloudflare",
		StatusCodes:  []int{200},
		Ping:         pingConfig{Enable: true, MaxMs: 300},
		Jitter:       jitterConfig{Enable: true, MaxMs: 50, Samples: 5, IntervalMs: 200},
		Download: downloadConfig{
			URL:         "https://speed.cloudflare.com/__down?bytes=1000000",
			TargetBytes: 1_000_000,
			TimeoutMs:   5000,
		},
	}
}

// parseConfig reads the app's JSON over the defaults and checks it.
func parseConfig(raw string) (config, error) {
	conf := defaults()
	if strings.TrimSpace(raw) != "" {
		if err := json.Unmarshal([]byte(raw), &conf); err != nil {
			return config{}, fmt.Errorf("config: %w", err)
		}
	}
	if conf.SNI == "" {
		return config{}, errors.New("config: sni is required")
	}
	if conf.Host == "" {
		conf.Host = conf.SNI
	}
	if !strings.HasPrefix(conf.Path, "/") {
		conf.Path = "/" + conf.Path
	}
	if conf.Port <= 0 || conf.Port > 65535 {
		return config{}, fmt.Errorf("config: port %d out of range", conf.Port)
	}
	if _, ok := helloFor(conf.Fingerprint); !ok {
		return config{}, fmt.Errorf("config: unknown fingerprint %q", conf.Fingerprint)
	}
	// Hard ceilings, whatever the app asks: this runs on a phone.
	conf.MaxIPs = clamp(conf.MaxIPs, 1, 5000)
	conf.StopAfter = clamp(conf.StopAfter, 1, conf.MaxIPs)
	conf.Workers = clamp(conf.Workers, 1, 64)
	conf.MaxLatencyMs = clamp(conf.MaxLatencyMs, 100, 10_000)
	conf.TCPTimeoutMs = clamp(conf.TCPTimeoutMs, 100, 10_000)
	conf.Jitter.Samples = clamp(conf.Jitter.Samples, 2, 20)
	conf.Download.TargetBytes = clamp(conf.Download.TargetBytes, 10_000, 20_000_000)
	conf.Download.TimeoutMs = clamp(conf.Download.TimeoutMs, 500, 30_000)
	return conf, nil
}

func clamp(v, lo, hi int) int {
	if v < lo {
		return lo
	}
	if v > hi {
		return hi
	}
	return v
}
