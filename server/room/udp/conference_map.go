package udp

import "sync"

type ConferenceMap struct{
	mx sync.RWMutex
	data map[string][]string	// conference id -> list of users
	client *Client
}

func NewConferenceMap(client *Client) *ConferenceMap {
	return &ConferenceMap{
		data: make(map[string][]string),
		client: client,
	}
}

func (m *ConferenceMap) AddUserToConference(conference string, user string) {
	m.mx.Lock()
	defer m.mx.Unlock()

	_, ok := m.data[conference]
	if !ok {
		m.data[conference] = []string{user}
	} else {
		m.data[conference] = append(m.data[conference], user)
	}
}

func (m *ConferenceMap) NotifyUsers(except string, data []byte) {
	m.mx.RLock()
	defer m.mx.RUnlock()
	return
}