package main

import (
	"context"
	"github.com/susliko/zumba/server/room/common"
	"github.com/susliko/zumba/server/room/tcp"
	"github.com/susliko/zumba/server/room/udp"

	"github.com/heetch/confita"
	"github.com/heetch/confita/backend/env"
)

type Config struct {
	logger *common.LoggerConfig	 `config:"logger"`
	http *tcp.HTTPServerConfig	 `config:"http"`
	udp  *udp.ServerConfig		 `config:"udp"`
}

func LoadConfig(ctx context.Context) (*Config, error) {
	// default values
	cfg := Config{
		logger: common.NewDefaultLoggerConfig(),
		http:  tcp.NewDefaultHTTPServerConfig(),
		udp: udp.NewDefaultServerConfig(),
	}

	err := confita.NewLoader(env.NewBackend()).Load(ctx, &cfg)
	if err != nil {
		return nil, err
	}

	return &cfg, nil
}