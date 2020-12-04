package udp

import (
	"context"
	"fmt"
	"github.com/susliko/zumba/server/room/conference"
	"go.uber.org/zap"
	"golang.org/x/sync/errgroup"
	"net"
	"time"
)

type ServerConfig struct {
	Host          string
	AudioPort     uint16
	VideoPort     uint16
	TextPort      uint16
	MaxBufferSize uint16
}

func NewDefaultServerConfig() *ServerConfig {
	return &ServerConfig{
		Host:          "",
		AudioPort:     5001,
		VideoPort:     5002,
		TextPort:      5003,
		MaxBufferSize: 1024,
	}
}

func (config *ServerConfig) AudioAddress() string {
	return fmt.Sprintf("%s:%d", config.Host, config.AudioPort)
}

func (config *ServerConfig) VideoAddress() string {
	return fmt.Sprintf("%s:%d", config.Host, config.VideoPort)
}

func (config *ServerConfig) TextAddress() string {
	return fmt.Sprintf("%s:%d", config.Host, config.TextPort)
}

type Server struct {
	logger *zap.SugaredLogger
	config *ServerConfig
	conferenceMap *conference.ConferenceMap
	cache *AddressCache
}

func NewServer(
	logger *zap.SugaredLogger,
	config *ServerConfig,
	conferenceMap *conference.ConferenceMap,
	cache *AddressCache,
) *Server{
	return &Server{
		logger: logger,
		config: config,
		conferenceMap: conferenceMap,
		cache: cache,
	}
}

func (server *Server) Run(ctx context.Context) error {
	wg, ctx := errgroup.WithContext(ctx)
	wg.Go(func() error {
		return server.runSocket(ctx, server.config.AudioAddress())
	})

	wg.Go(func() error {
		return server.runSocket(ctx, server.config.VideoAddress())
	})

	wg.Go(func() error {
		return server.runSocket(ctx, server.config.TextAddress())
	})

	return wg.Wait()
}

func (server *Server) runSocket(ctx context.Context, address string) error {
	pc, err := net.ListenPacket("udp", address)
	if err != nil {
		return err
	}

	wg, ctx := errgroup.WithContext(ctx)
	wg.Go(func() error {
		buffer := make([]byte, server.config.MaxBufferSize)
		for {
			select {
			case <-ctx.Done():
				server.logger.Error("ctx done with %v", ctx.Err())
				return nil
			default:
			}

			n, addr, err := pc.ReadFrom(buffer)
			if err != nil {
				server.logger.Errorf("while reading from upd socket an err occurred: %v", err)
				continue
			}
			server.logger.Infof("udp-packet-received: bytes=%d; from=%s", n, addr.String())

			if n < 2 {
				server.logger.Errorf("%d bytes is not enough to parse", n)
				continue
			}

			msg, err := ParseMessageFromBytes(buffer[:n])
			if err != nil {
				server.logger.Errorf("while parsing msg an error occurred: %v", err)
				continue
			}
			server.cache.Save(msg.User, addr)
			server.logger.Infof("received msg for conference %d from user %d", msg.Conference, msg.User)

			users, err := server.conferenceMap.GetConferenceUsers(msg.Conference)
 			if err != nil {
 				server.logger.Errorf("while getting conference users an error occurred: %v", err)
 				continue
			}

			for _, user := range users {
				if user == msg.User {
					continue
				}

				go func(user uint8, bytes []byte) {
					addr, isHaveAddr := server.cache.Get(user)
					if !isHaveAddr {
						server.logger.Errorf("can't find addr for user: %d", user)
						return
					}

					err = pc.SetWriteDeadline(time.Now().Add(100 * time.Millisecond))
					if err != nil {
						server.logger.Errorf("while setting udp-write deadline err occurred: %v", err)
						return
					}

					n, err = pc.WriteTo(bytes, addr)
					if err != nil {
						server.logger.Errorf("while udp=writing to %s an err occurred: %v", addr, err)
						return
					}
					server.logger.Infof("udp-packet-written: bytes=%d to=%s", n, addr.String())
				}(user, msg.Content)
			}
		}
	})

	wg.Go(func() error {
		<-ctx.Done()
		return pc.Close()
	})

	return wg.Wait()
}