package udp

import (
	"fmt"
	"strconv"
)

type Message struct {
	Conference uint8
	User uint8
	Content []byte
}

func ParseMessageFromBytes(bytes []byte) (*Message, error) {
	conferenceToInt, err := strconv.Atoi(string(bytes[0]))
	if err != nil {
		return nil, fmt.Errorf("can't parse first byte to int: %v", err)
	}

	userToInt, err := strconv.Atoi(string(bytes[1]))
	if err != nil {
		return nil, fmt.Errorf("can't parse second byte to int: %v", err)
	}

	return &Message{
		Conference: uint8(conferenceToInt),
		User: uint8(userToInt),
		Content: bytes[2:],
	}, nil
}