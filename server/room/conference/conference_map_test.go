package conference

import (
	"encoding/json"
	"github.com/stretchr/testify/assert"
	"github.com/susliko/zumba/server/room/common"
	"testing"
)

func TestListConferences(t *testing.T) {
	conferenceMap := NewConferenceMap()
	err := conferenceMap.AddConference(1)
	assert.NoError(t, err)

	conferenceMap.AddUserToConference(1, 1)
	assert.NoError(t, err)

	conferenceMap.AddUserToConference(1, 2)
	assert.NoError(t, err)

	conferences := conferenceMap.ListConferences()
	assert.Equal(t, conferences, map[uint8]common.JsonableUint8Slice{
		1: []uint8{1, 2},
	})

	conferencesJson, err := json.Marshal(conferences)
	assert.NoError(t, err)
	assert.Equal(t, string(conferencesJson), "{\"1\":[1,2]}")
}

func TestAddUsersToConferencesWithoutAddConference(t *testing.T) {
	conferenceMap := NewConferenceMap()
	conferenceMap.AddUserToConference(1, 1)
	conferenceMap.AddUserToConference(1, 2)

	conferences := conferenceMap.ListConferences()
	assert.Equal(t, conferences, map[uint8]common.JsonableUint8Slice{
		1: []uint8{1, 2},
	})
}

func TestGetConferenceUsers(t *testing.T) {
	conferenceMap := NewConferenceMap()
	conferenceMap.AddUserToConference(1, 1)
	conferenceMap.AddUserToConference(1, 2)

	conferencesUsers, err := conferenceMap.GetConferenceUsers(1)
	assert.NoError(t, err)

	assert.Equal(t, conferencesUsers, common.JsonableUint8Slice{1, 2})
}

func TestAddConferenceAlreadyExistError(t *testing.T) {
	conferenceMap := NewConferenceMap()
	err := conferenceMap.AddConference(1)
	assert.NoError(t, err)

	err = conferenceMap.AddConference(1)
	assert.Equal(t, err, AlreadyExistError)
}

func TestRemoveConference(t *testing.T) {
	conferenceMap := NewConferenceMap()
	err := conferenceMap.AddConference(1)
	assert.NoError(t, err)

	conferences := conferenceMap.ListConferences()
	assert.Equal(t, conferences, map[uint8]common.JsonableUint8Slice{
		1: []uint8{},
	})
}

func TestRemoveUser(t *testing.T) {
	conferenceMap := NewConferenceMap()
	conferenceMap.AddUserToConference(1, 1)
	err := conferenceMap.RemoveUserFromConference(1, 1)
	assert.NoError(t, err)

	conferences := conferenceMap.ListConferences()
	assert.Equal(t, conferences, map[uint8]common.JsonableUint8Slice{
		1: []uint8{},
	})
}