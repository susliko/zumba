package tcp

import (
	"context"
	"fmt"
	"github.com/go-chi/chi"
	"github.com/go-chi/chi/middleware"
	"go.uber.org/zap"
	"golang.org/x/sync/errgroup"
	"net/http"
)

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
}

func NewHTTPServer(logger *zap.SugaredLogger, config *HTTPServerConfig) *HTTPServer{
	return &HTTPServer{
		logger: logger,
		config: config,
	}
}

func (server *HTTPServer) Run(ctx context.Context) error {
	r := chi.NewRouter()
	r.Use(middleware.Logger)

	r.Get("/ping", func(w http.ResponseWriter, r *http.Request) {
		_, err := w.Write([]byte("OK!"))
		if err != nil {
			server.logger.Errorf("while responding to ping an error occurred: %v", err)
		}
	})

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