package udp

import (
	"github.com/stretchr/testify/assert"
	"net"
	"testing"
)

func TestAddressCache(t *testing.T) {
	iaddrs, err := net.InterfaceAddrs()
	assert.NoError(t, err)

	addr := iaddrs[0]

	addressCache := NewAddressCache()
	addressCache.Save(1, addr)

	getAddr, isOk := addressCache.Get(1)
	assert.Equal(t, addr, getAddr)
	assert.Equal(t, isOk, true)

	getAddr, isOk = addressCache.Get(2)
	assert.Nil(t, getAddr)
	assert.Equal(t, isOk, false)
}
