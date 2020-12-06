package common

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestMarshalJSON(t *testing.T) {
	slice := JsonableUint8Slice{1, 2, 3}
	slice = append(slice, 4)

	sliceMarshalled, err := slice.MarshalJSON()
	assert.NoError(t, err)

	assert.Equal(t, string(sliceMarshalled), "[1,2,3,4]")
}
