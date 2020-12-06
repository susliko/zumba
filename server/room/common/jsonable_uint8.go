package common

import (
	"fmt"
	"strings"
)

type JsonableUint8Slice []uint8

func (u JsonableUint8Slice) MarshalJSON() ([]byte, error) {
	if u == nil {
		return []byte("null"), nil
	}

	result := strings.Join(strings.Fields(fmt.Sprintf("%d", u)), ",")
	return []byte(result), nil
}
