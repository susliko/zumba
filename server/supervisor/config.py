from pydantic import BaseSettings, RedisDsn


class Config(BaseSettings):
    REDIS_DSN: RedisDsn = 'redis://localhost:6379'


config = Config()
