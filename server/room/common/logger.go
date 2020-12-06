package common

import (
	"time"

	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

type LoggerConfig struct {
	Level zapcore.Level	 	 `config:"level"`
	OutputPaths []string	 `config:"output_paths"`
}

func NewDefaultLoggerConfig() *LoggerConfig {
	return &LoggerConfig{
		Level: zap.DebugLevel,
		OutputPaths: []string{"stdout"},
	}
}

func Iso3339CleanTime(t time.Time) string {
	return t.UTC().Format("2006-01-02T15:04:05.000000Z")
}

func Iso3339CleanTimeEncoder(t time.Time, enc zapcore.PrimitiveArrayEncoder) {
	enc.AppendString(Iso3339CleanTime(t))
}

func NewLogger(config *LoggerConfig)  (*zap.SugaredLogger, error) {
	encoderConfig := zap.NewProductionEncoderConfig()
	encoderConfig.TimeKey = "time"
	encoderConfig.EncodeTime = Iso3339CleanTimeEncoder

	productionConfig := zap.NewProductionConfig()
	productionConfig.Level = zap.NewAtomicLevelAt(config.Level)
	productionConfig.EncoderConfig = encoderConfig
	productionConfig.OutputPaths = config.OutputPaths

	zapLogger, err := productionConfig.Build()
	if err != nil {
		return nil, err
	}

	return zapLogger.Sugar(), nil
}