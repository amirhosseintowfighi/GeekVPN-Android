# Xray core patches

`scripts/build-libv2ray.sh` copies the `github.com/xtls/xray-core` module at the
version `AndroidLibXrayLite/go.mod` pins, applies every `*.patch` here, runs the
`*_test.go` files here (`go test ./infra/conf -run TestGeekVPN`) and builds the
AAR against that copy. A patch that no longer applies fails the build.

## `allow-plain-vless.patch`

Xray refuses a VLESS outbound with no TLS/REALITY and no VLESS encryption to a
public address ("vless without TLS or other encryption is prohibited"). GeekVPN's
tunnel services still hand out such links (ws, `security=none`, behind
Cloudflare's plain-HTTP ports such as 2086), so without this patch they cannot
connect at all. Plain Trojan stays refused.

Plain VLESS sends the UUID and the traffic in the clear to anyone on the path.
Drop this patch once every tunnel service uses TLS (or VLESS encryption).
