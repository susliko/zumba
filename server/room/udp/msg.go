package udp

type Message struct {
	Conference uint8
	User uint8
	Content []byte
}

func ParseMessageFromBytes(bytes []byte) (*Message, error) {
	return &Message{
		Conference: uint8(bytes[0]),
		User: uint8(bytes[1]),
		Content: bytes[2:],
	}, nil
}