package libv2ray

import "github.com/xtls/xray-core/common/geekstats"

// ActiveConnections is how many outbound connections the running core is
// carrying (GeekVPN; counted by scripts/xray-patches/connection-count.patch).
// It reads this process's core, so only the VPN process gets a real number.
func ActiveConnections() int64 {
	return geekstats.Active.Load()
}
