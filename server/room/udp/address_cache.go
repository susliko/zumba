package udp

import (
	"net"
	"sync"
)

type AddressCache struct {
	mx sync.RWMutex
	cache map[uint8]net.Addr // user_id -> last received address
}

func NewAddressCache() *AddressCache{
	return &AddressCache{
		cache: make(map[uint8]net.Addr),
	}
}

func (c *AddressCache) Save(id uint8, address net.Addr) {
	c.mx.Lock()
	defer c.mx.Unlock()
	c.cache[id] = address
}

func (c *AddressCache) Get(id uint8) (net.Addr, bool) {
	c.mx.Lock()
	value, is := c.cache[id]
	c.mx.Unlock()
	return value, is
}


