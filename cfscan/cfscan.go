// Package cfscan is GeekVPN's Cloudflare clean-IP scanner.
//
// It is compiled into libv2ray.aar by the same gomobile bind as the Xray core
// (scripts/build-libv2ray.sh). Two gomobile AARs cannot share one app: each
// carries its own Go runtime and its own go.* Java classes, and the second one
// to load fails. Binding both packages together gives one runtime and one set
// of glue classes, with Kotlin reaching this package as cfscan.Cfscan.
package cfscan

// version is bumped whenever the scanner's behaviour changes, so a bug report
// from the About screen says which engine produced it.
const version = "0.1.0"

// Version identifies the scanner engine built into this AAR.
func Version() string {
	return version
}
