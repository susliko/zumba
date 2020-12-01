package udp

import (
	"context"
	"fmt"
	"go.uber.org/zap"
	"golang.org/x/sync/errgroup"
	"net"
	"time"
)

type ServerConfig struct {
	Host string
	Port uint16
	MaxBufferSize uint16
}

func NewDefaultServerConfig() *ServerConfig {
	return &ServerConfig{
		Host: "",
		Port: 5001,
		MaxBufferSize: 1024,
	}
}

func (config *ServerConfig) Address() string {
	return fmt.Sprintf("%s:%d", config.Host, config.Port)
}

type Server struct {
	logger *zap.SugaredLogger
	config *ServerConfig
}

func NewServer(logger *zap.SugaredLogger, config *ServerConfig) *Server{
	return &Server{
		logger: logger,
		config: config,
	}
}

func (server *Server) Run(ctx context.Context) error {
	pc, err := net.ListenPacket("udp", server.config.Address())
	if err != nil {
		return err
	}

	wg, ctx := errgroup.WithContext(ctx)
	wg.Go(func() error {
		// TODO: how to stop this cycle in case of ctx.Done ?
		buffer := make([]byte, server.config.MaxBufferSize)
		for {
			n, addr, err := pc.ReadFrom(buffer)
			if err != nil {
				server.logger.Errorf("while reading from upd socket an err occurred: %v", err)
				continue
			}

			server.logger.Infof("udp-packet-received: bytes=%d; from=%s", n, addr.String())

			// TODO data := buffer[:n]

			d := time.Second // TODO: move to config
			deadline := time.Now().Add(d)
			err = pc.SetWriteDeadline(deadline)
			if err != nil {
				server.logger.Errorf("while setting udp-write deadline err occurred: %v", err)
				continue
			}

			// Write the packet's contents back to the client.
			n, err = pc.WriteTo(buffer[:n], addr)
			if err != nil {
				server.logger.Errorf("while udp=writing to %s an err occurred: %v", addr, err)
				continue
			}

			server.logger.Infof("udp-packet-written: bytes=%d to=%s", n, addr.String())
		}
		return nil
	})

	wg.Go(func() error {
		<-ctx.Done()
		return pc.Close()
	})

	return wg.Wait()
}

