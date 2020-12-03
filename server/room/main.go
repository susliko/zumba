package main

import (
	"context"
	"github.com/susliko/zumba/server/room/common"
	"github.com/susliko/zumba/server/room/conference"
	"github.com/susliko/zumba/server/room/tcp"
	"github.com/susliko/zumba/server/room/udp"
	"go.uber.org/zap"
	"golang.org/x/sync/errgroup"
	"os"
	"os/signal"
	"syscall"
)

func run(logger *zap.SugaredLogger) error {
	ctx := context.Background()

	conferenceMap := conference.NewConferenceMap()

	httpServerConfig := tcp.NewDefaultHTTPServerConfig()
	httpServer := tcp.NewHTTPServer(logger, httpServerConfig, conferenceMap)

	udpServerConfig := udp.NewDefaultServerConfig()
	updServer := udp.NewServer(logger, udpServerConfig)

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
	loggerConfig := common.NewDefaultLoggerConfig()
	logger, err := common.NewLogger(loggerConfig)
	if err != nil {
		panic(err)
	}

	err = run(logger)
	if err != nil {
		logger.Errorf("%v", err)
	}
}
