package udp

import (
	"context"
	"go.uber.org/zap"
	"io"
	"net"
	"time"
)

type ClientConfig struct {
	Host string
	Port uint16
	MaxBufferSize uint16
}

func NewDefaultClientConfig() *ClientConfig {
	return &ClientConfig{
		Host: "",
		Port: 6000,
		MaxBufferSize: 1024,
	}
}


func (config *ClientConfig) laddr() *net.UDPAddr {
	return &net.UDPAddr{
		IP: net.IP(config.Host),
		Port: int(config.Port),
	}
}

type Client struct {
	logger *zap.SugaredLogger
	config *ClientConfig
}

func NewClient(logger *zap.SugaredLogger, config *ClientConfig) *Client{
	return &Client{
		logger: logger,
		config: config,
	}
}

func (client *Client) Send(ctx context.Context, address string, reader io.Reader) error {
	raddr, err := net.ResolveUDPAddr("udp", address)
	if err != nil {
		return err
	}

	conn, err := net.DialUDP("udp", client.config.laddr(), raddr)
	if err != nil {
		return err
	}

	done := make(chan error, 1)
	go func() {
		n, err := io.Copy(conn, reader)
		if err != nil {
			done <- err
			return
		}
		client.logger.Infof("packet-written: bytes=%d", n)

		buffer := make([]byte, client.config.MaxBufferSize)

		d := time.Second // TODO: move to config
		deadline := time.Now().Add(d)
		err = conn.SetReadDeadline(deadline)
		if err != nil {
			done <- err
			return
		}

		nRead, addr, err := conn.ReadFrom(buffer)
		if err != nil {
			done <- err
			return
		}

		client.logger.Infof("udp-packet-received: bytes=%d from=%s", nRead, addr.String())

		done <- nil
	}()

	select {
	case <-ctx.Done():
		client.logger.Info("cancelled")
		err = ctx.Err()
	case err = <-done:
	}

	return conn.Close()
}