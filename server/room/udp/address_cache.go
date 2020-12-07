package udp

import (
	"fmt"
	"net"
	"sync"
)

type AddressType int
const (
	AudioType = AddressType(0)
	VideoType = AddressType(1)
	TextType = AddressType(2)
)

type AddressCache struct {
	mx sync.RWMutex
	cache map[string]net.Addr // {user_id}_{address_type} -> last received address
}

func NewAddressCache() *AddressCache{
	return &AddressCache{
		cache: make(map[string]net.Addr),
	}
}

func (c *AddressCache) Save(id uint8, addressType AddressType, address net.Addr) {
	c.mx.Lock()
	defer c.mx.Unlock()
	c.cache[c.getKey(id, addressType)] = address
}

func (c *AddressCache) Get(id uint8, addressType AddressType) (net.Addr, bool) {
	c.mx.Lock()
	value, is := c.cache[c.getKey(id, addressType)]
	c.mx.Unlock()
	return value, is
}

func (c *AddressCache) getKey(id uint8, addressType AddressType) string  {
	return fmt.Sprintf("%d_%d", id, addressType)
}