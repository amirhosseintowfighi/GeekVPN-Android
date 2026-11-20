package cfscan

import (
	"errors"
	"fmt"
	"math/rand/v2"
	"net/netip"
	"strings"
)

// parseRanges reads IPv4 CIDRs, one per line; blank lines and # comments are
// skipped, and so are IPv6 lines (see parseRanges6).
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

// parseRanges6 reads the IPv6 CIDRs among the same lines. None is not an
// error: the app sends them only when the phone has IPv6 to scan with.
func parseRanges6(text string) []netip.Prefix {
	var out []netip.Prefix
	for line := range strings.Lines(text) {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		prefix, err := netip.ParsePrefix(line)
		if err != nil || prefix.Addr().Is4() {
			continue
		}
		out = append(out, prefix.Masked())
	}
	return out
}

// sample6 picks up to n distinct addresses in the IPv6 prefixes: a prefix at
// random, then random host bits. An IPv6 range is far too large to weigh by
// size or to walk.
func sample6(prefixes []netip.Prefix, n int, rng *rand.Rand) []netip.Addr {
	if len(prefixes) == 0 || n <= 0 {
		return nil
	}
	seen := make(map[netip.Addr]struct{}, n)
	out := make([]netip.Addr, 0, n)
	for attempts := 0; len(out) < n && attempts < n*20; attempts++ {
		p := prefixes[rng.IntN(len(prefixes))]
		base := p.Addr().As16()
		var random [16]byte
		hi, lo := rng.Uint64(), rng.Uint64()
		for i := 0; i < 8; i++ {
			random[i] = byte(hi >> (56 - 8*i))
			random[8+i] = byte(lo >> (56 - 8*i))
		}
		// Keep the prefix's bits, take the rest from random.
		var addr [16]byte
		for i := 0; i < 16; i++ {
			keep := p.Bits() - 8*i
			var mask byte
			switch {
			case keep >= 8:
				mask = 0xff
			case keep <= 0:
				mask = 0
			default:
				mask = byte(0xff << (8 - keep))
			}
			addr[i] = base[i]&mask | random[i]&^mask
		}
		a := netip.AddrFrom16(addr)
		if _, dup := seen[a]; dup {
			continue
		}
		seen[a] = struct{}{}
		out = append(out, a)
	}
	return out
}

// interleave alternates a and b, so the workers try both families from the start.
func interleave(a, b []netip.Addr) []netip.Addr {
	out := make([]netip.Addr, 0, len(a)+len(b))
	for i := 0; i < len(a) || i < len(b); i++ {
		if i < len(a) {
			out = append(out, a[i])
		}
		if i < len(b) {
			out = append(out, b[i])
		}
	}
	return out
}
