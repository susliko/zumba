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
	Host          string	 `config:"host"`
	AudioPort     uint16	 `config:"audio_port"`
	VideoPort     uint16	 `config:"video_port"`
	TextPort      uint16	 `config:"text_port"`
	MaxBufferSize uint16	 `config:"max_buffer_size"`
}

func NewDefaultServerConfig() *ServerConfig {
	return &ServerConfig{
		Host:          "",
		AudioPort:     5001,
		VideoPort:     5002,
		TextPort:      5003,
		MaxBufferSize: 8192,
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
	lastMsgId uint64
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
		lastMsgId: 0,
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
			server.logger.Infof("udp-packet-received: bytes=%d; from=%s; id=%d", n, addr.String(), server.lastMsgId)

			tmp := make([]byte, n)
			copy(tmp, buffer[:n])

			go func(tmp []byte, n int, msgId uint64, addr net.Addr) {
				logger := server.logger.With(zap.Uint64("msg_id", msgId))
				msg, err := ParseMessageFromBytes(n, tmp)
				if err != nil {
					logger.Errorf("while parsing msg an error occurred: %v", err)
					return
				}
				server.cache.Save(msg.User, addr)
				logger = logger.With(zap.Uint8("conference", msg.Conference), zap.Uint8("user", msg.User))
				if n <= 2 {
					// This is just ACK message, without data
					return
				}

				logger.Infof("start sending msg")

				users, err := server.conferenceMap.GetConferenceUsers(msg.Conference)
				if err != nil {
					logger.Errorf("while getting conference users an error occurred: %v", err)
					return
				}
				logger.Debugf("send msg to users: %v", users)

				for _, user := range users {
					user := user
					if user == msg.User {
						logger.Debugf("don't send msg to user: %d", user)
						continue
					}
					logger.Debugf("send msg to user: %d", user)

					go func(user uint8, bytes []byte) {
						logger.Debugf("get addr for user %d", user)
						addr, isHaveAddr := server.cache.Get(user)
						if !isHaveAddr {
							logger.Errorf("can't find addr for user: %d", user)
							return
						}
						logger.Debugf("addr for user %d is %v", user, addr)

						err = pc.SetWriteDeadline(time.Now().Add(100 * time.Millisecond))
						if err != nil {
							logger.Errorf("while setting udp-write deadline err occurred: %v", err)
							return
						}

						n, err = pc.WriteTo(bytes, addr)
						if err != nil {
							logger.Errorf("while udp writing to %s an err occurred: %v", addr, err)
							return
						}
						logger.Infof("udp-packet-written: bytes=%d to=%s", n, addr.String())
					}(user, msg.Content)
				}
			}(tmp, n, server.lastMsgId, addr)
			server.lastMsgId += 1
		}
	})

	wg.Go(func() error {
		<-ctx.Done()
		return pc.Close()
	})

	return wg.Wait()
}