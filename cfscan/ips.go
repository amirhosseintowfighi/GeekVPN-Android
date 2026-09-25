package cfscan

import (
	"errors"
	"fmt"
	"math/rand/v2"
	"net/netip"
	"strings"
)

// parseRanges reads IPv4 CIDRs, one per line; blank lines and # comments are
// skipped. IPv6 is not scanned: the customer's config names one IPv4 address.
func parseRanges(text string) ([]netip.Prefix, error) {
	var out []netip.Prefix
	for line := range strings.Lines(text) {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		prefix, err := netip.ParsePrefix(line)
		if err != nil {
			return nil, fmt.Errorf("ranges: %w", err)
		}
		if !prefix.Addr().Is4() {
			continue
		}
		out = append(out, prefix.Masked())
	}
	if len(out) == 0 {
		return nil, errors.New("ranges: no IPv4 range")
	}
	return out, nil
}

// Sample picks up to n distinct addresses across the prefixes, each prefix in
// proportion to its size. Walking every address the way cf-scanner does would
// mean over a million strings on a phone for a scan that stops after a few
// hundred.
func sample(prefixes []netip.Prefix, n int, rng *rand.Rand) []netip.Addr {
	sizes := make([]uint64, len(prefixes))
	var total uint64
	for i, p := range prefixes {
		sizes[i] = uint64(1) << (32 - p.Bits())
		total += sizes[i]
	}
	if total == 0 || n <= 0 {
		return nil
	}
	if uint64(n) > total {
		n = int(total)
	}
	seen := make(map[netip.Addr]struct{}, n)
	out := make([]netip.Addr, 0, n)
	// Bounded: with many duplicates (tiny ranges) give up rather than spin.
	for attempts := 0; len(out) < n && attempts < n*20; attempts++ {
		offset := rng.Uint64N(total)
		i := 0
		for offset >= sizes[i] {
			offset -= sizes[i]
			i++
		}
		base := prefixes[i].Addr().As4()
		value := uint32(base[0])<<24 | uint32(base[1])<<16 | uint32(base[2])<<8 | uint32(base[3])
		value += uint32(offset)
		addr := netip.AddrFrom4([4]byte{byte(value >> 24), byte(value >> 16), byte(value >> 8), byte(value)})
		// The network and broadcast addresses of a /24 never answer.
		if last := byte(value); last == 0 || last == 255 {
			continue
		}
		if _, dup := seen[addr]; dup {
			continue
		}
		seen[addr] = struct{}{}
		out = append(out, addr)
	}
	return out
}
