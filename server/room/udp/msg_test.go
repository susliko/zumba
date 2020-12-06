package udp

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestParseMessageFromBytes(t *testing.T) {
	bytes := []byte{1, 2, 3, 4, 5}
	msg, err := ParseMessageFromBytes(bytes)
	assert.NoError(t, err)

	assert.Equal(t, msg.Conference, uint8(1))
	assert.Equal(t, msg.User, uint8(2))
	assert.Equal(t, msg.Content, bytes)
}