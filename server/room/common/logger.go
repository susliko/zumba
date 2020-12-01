package common

import (
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

type LoggerConfig struct {
	Level zapcore.Level
	OutputPaths []string
}

func NewDefaultLoggerConfig() *LoggerConfig {
	return &LoggerConfig{
		Level: zap.DebugLevel,
		OutputPaths: []string{"stdout"},
	}
}

func NewLogger(config *LoggerConfig)  (*zap.SugaredLogger, error) {
	productionConfig := zap.NewProductionConfig()
	productionConfig.Level = zap.NewAtomicLevelAt(config.Level)
	productionConfig.OutputPaths = config.OutputPaths

	zapLogger, err := productionConfig.Build()
	if err != nil {
		return nil, err
	}

	return zapLogger.Sugar(), nil
}