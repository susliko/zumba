package conference

import (
	"errors"
	"sync"
)

var (
	AlreadyExistError       = errors.New("conference already in map")
	ConferenceNotFoundError = errors.New("conference not found in map")
	UserNotFoundError       = errors.New("user not found in map")
)

type ConferenceMap struct{
	mx sync.RWMutex
	data map[uint8]map[uint8]bool	// conference id -> set of users
}

func NewConferenceMap() *ConferenceMap {
	return &ConferenceMap{
		data: make(map[uint8]map[uint8]bool),
	}
}

func (m *ConferenceMap) AddConference(conference uint8) error {
	m.mx.Lock()
	defer m.mx.Unlock()

	if _, ok := m.data[conference]; ok {
		return AlreadyExistError
	}
	m.data[conference] = make(map[uint8]bool)
	return nil
}

func (m *ConferenceMap) AddUserToConference(conference uint8, user uint8) {
	m.mx.Lock()
	defer m.mx.Unlock()

	_, ok := m.data[conference]
	if !ok {
		m.data[conference] = map[uint8]bool{user: true}
	} else {
		m.data[conference][user] = true
	}
}

func (m *ConferenceMap) GetConferenceUsers(conference uint8) ([]uint8, error) {
	m.mx.RLock()
	defer m.mx.RUnlock()

	if _, ok := m.data[conference]; !ok {
		return nil, ConferenceNotFoundError
	}

	result := make([]uint8, len(m.data[conference]))
	for user := range m.data[conference] {
		result = append(result, user)
	}
	return result, nil
}

func (m *ConferenceMap) RemoveConference(conference uint8) error {
	m.mx.Lock()
	defer m.mx.Unlock()
	if _, ok := m.data[conference]; !ok {
		return ConferenceNotFoundError
	}
	delete(m.data, conference)
	return nil
}

func (m *ConferenceMap) RemoveUserFromConference(conference uint8, user uint8) error {
	m.mx.Lock()
	defer m.mx.Unlock()
	if _, ok := m.data[conference]; !ok {
		return ConferenceNotFoundError
	}

	if _, ok := m.data[conference][user]; !ok {
		return UserNotFoundError
	}

	delete(m.data[conference], user)
	return nil
}

func (m *ConferenceMap) ListConferences() map[uint8][]uint8 {
	m.mx.RLock()
	defer m.mx.RUnlock()

	result := make(map[uint8][]uint8, len(m.data))
	for conference := range m.data {
		users := make([]uint8, 0, len(m.data[conference]))
		for user := range m.data[conference] {
			users = append(users, user)
		}
		result[conference] = users
	}
	return result
}