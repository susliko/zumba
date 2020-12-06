package main

import (
	"context"
	"os"
	"os/signal"
	"syscall"

	"go.uber.org/zap"
	"golang.org/x/sync/errgroup"

	"github.com/susliko/zumba/server/room/common"
	"github.com/susliko/zumba/server/room/conference"
	"github.com/susliko/zumba/server/room/tcp"
	"github.com/susliko/zumba/server/room/udp"
)

func run(ctx context.Context, logger *zap.SugaredLogger, config *Config) error {
	conferenceMap := conference.NewConferenceMap()
	cache := udp.NewAddressCache()

	httpServer := tcp.NewHTTPServer(logger, config.http, conferenceMap)
	updServer := udp.NewServer(logger, config.udp, conferenceMap, cache)

	ctx, cancel := context.WithCancel(ctx)
	wg, ctx := errgroup.WithContext(ctx)

	sigs := make(chan os.Signal, 1)
	signal.Notify(sigs, syscall.SIGKILL, syscall.SIGTERM)
	wg.Go(func() error {
		sig := <- sigs
		logger.Infof("Received %s signal, start shutdown", sig)
		cancel()
		return nil
	})

	wg.Go(func() error {
		logger.Infof("Start http tcp server")
		return httpServer.Run(ctx)
	})

	wg.Go(func() error {
		logger.Infof("Start udp server")
		return updServer.Run(ctx)
	})

	return wg.Wait()
}

func main() {
	ctx := context.Background()
	config, err := LoadConfig(ctx)
	if err != nil {
		panic(err)
	}

	loggerConfig := common.NewDefaultLoggerConfig()
	logger, err := common.NewLogger(loggerConfig)
	if err != nil {
		panic(err)
	}

	err = run(ctx, logger, config)
	if err != nil {
		logger.Errorf("%v", err)
	}
}
