package udp

import "fmt"

type Message struct {
	Conference uint8
	User uint8
	Content []byte
}

func ParseMessageFromBytes(n int, bytes []byte) (*Message, error) {
	if n < 2 {
		return nil, fmt.Errorf("%d bytes is not enough to parse", n)
	}

	return &Message{
		Conference: bytes[0],
		User: bytes[1],
		Content: bytes,
	}, nil
}