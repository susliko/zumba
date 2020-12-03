package tcp

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"

	"github.com/go-chi/chi"
	"github.com/go-chi/chi/middleware"
	"go.uber.org/zap"
	"golang.org/x/sync/errgroup"

	"github.com/susliko/zumba/server/room/conference"
)

type conferenceRequest struct {
	Conference *int8	`json:"conference"`
}

type userRequest struct {
	Conference *int8	`json:"conference"`
	User	   *int8	`json:"user"`
}

type HTTPServerConfig struct {
	Host string
	Port uint16
}

func NewDefaultHTTPServerConfig() *HTTPServerConfig {
	return &HTTPServerConfig{
		Host: "",
		Port: 5000,
	}
}

type HTTPServer struct{
	logger *zap.SugaredLogger
	config *HTTPServerConfig
	conferenceMap *conference.ConferenceMap
}

func NewHTTPServer(
	logger *zap.SugaredLogger,
	config *HTTPServerConfig,
	conferenceMap *conference.ConferenceMap,
) *HTTPServer{
	return &HTTPServer{
		logger: logger,
		config: config,
		conferenceMap: conferenceMap,
	}
}

func (server *HTTPServer) Run(ctx context.Context) error {
	r := chi.NewRouter()
	r.Use(middleware.Logger)

	r.Get("/ping", server.Ping)

	r.Get("/start_conference", server.StartConference)
	r.Get("/stop_conference", server.StopConference)
	r.Get("/add_user", server.AddUser)
	r.Get("/remove_user", server.RemoveUser)
	r.Get("/list_conferences", server.ListConferences)

	srv := http.Server{
		Addr: fmt.Sprintf("%s:%d", server.config.Host, server.config.Port),
		Handler: chi.ServerBaseContext(ctx, r),
	}

	wg, ctx := errgroup.WithContext(ctx)
	wg.Go(func() error {
		return srv.ListenAndServe()
	})

	wg.Go(func() error {
		<-ctx.Done()
		return srv.Shutdown(ctx)
	})

	return wg.Wait()
}

func (server *HTTPServer) Ping(w http.ResponseWriter, r *http.Request) {
	_, err := w.Write([]byte("OK!"))
	if err != nil {
		server.logger.Errorf("while responding to ping an error occurred: %v", err)
	}
}

func (server *HTTPServer) StartConference(w http.ResponseWriter, r *http.Request) {
	request := server.parseConferenceRequest(w, r)
	if request != nil {
		err := server.conferenceMap.AddConference(*request.Conference)
		if err != nil {
			server.logger.Errorf("while adding conference an error occurred: %v", err)
			if errors.Is(err, conference.AlreadyExistError) {
				http.Error(w, err.Error(), http.StatusConflict)
			} else {
				http.Error(w, err.Error(), http.StatusInternalServerError)
			}
			return
		}
		w.WriteHeader(200)
	}
}

func (server *HTTPServer)  StopConference(w http.ResponseWriter, r *http.Request) {
	request := server.parseConferenceRequest(w, r)
	if request != nil {
		err := server.conferenceMap.RemoveConference(*request.Conference)
		if err != nil {
			server.logger.Errorf("while adding conference an error occurred: %v", err)
			if errors.Is(err, conference.ConferenceNotFoundError) {
				http.Error(w, err.Error(), http.StatusNotFound)
			} else {
				http.Error(w, err.Error(), http.StatusInternalServerError)
			}
			return
		}
		w.WriteHeader(200)
	}
}

func (server *HTTPServer) AddUser(w http.ResponseWriter, r *http.Request) {
	request := server.parseUserRequest(w, r)
	if request != nil {
		server.conferenceMap.AddUserToConference(*request.Conference, *request.User)
		w.WriteHeader(200)
	}
}

func (server *HTTPServer) RemoveUser(w http.ResponseWriter, r *http.Request) {
	request := server.parseUserRequest(w, r)
	if request != nil {
		err := server.conferenceMap.RemoveUserFromConference(*request.Conference, *request.User)
		if err != nil {
			server.logger.Errorf("while adding conference an error occurred: %v", err)
			if errors.Is(err, conference.ConferenceNotFoundError) {
				http.Error(w, err.Error(), http.StatusNotFound)
			} else {
				http.Error(w, err.Error(), http.StatusInternalServerError)
			}
			return
		}
		w.WriteHeader(200)
	}
}

func (server *HTTPServer) ListConferences(w http.ResponseWriter, r *http.Request) {
	conferences := server.conferenceMap.ListConferences()
	conferencesJson, err := json.Marshal(conferences)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_, err = w.Write(conferencesJson)
	if err != nil {
		server.logger.Errorf("while responding to list conferences: %v", err)
	}
}

func (server *HTTPServer) parseConferenceRequest(w http.ResponseWriter, r *http.Request) *conferenceRequest  {
	var request conferenceRequest

	err := json.NewDecoder(r.Body).Decode(&request)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return nil
	}

	if request.Conference == nil {
		http.Error(w, "specify `conference`", http.StatusBadRequest)
		return nil
	}

	return &request
}

func (server *HTTPServer) parseUserRequest(w http.ResponseWriter, r *http.Request) *userRequest {
	var request userRequest

	err := json.NewDecoder(r.Body).Decode(&request)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return nil
	}

	if request.Conference == nil {
		http.Error(w, "specify `conference`", http.StatusBadRequest)
		return nil
	}

	if request.User == nil {
		http.Error(w, "specify `user`", http.StatusBadRequest)
		return nil
	}

	return &request
}
