import logging.config
from os import path

from fastapi import FastAPI
from fastapi_route_logger_middleware import RouteLoggerMiddleware

from supervisor.routes.room import room_router
from supervisor.routes.user import user_router
from supervisor.routes.worker import worker_router

app = FastAPI()

log_file_path = path.join(path.dirname(path.abspath(__file__)), 'logging.conf')
logging.config.fileConfig(log_file_path, disable_existing_loggers=False)
app.add_middleware(RouteLoggerMiddleware)

app.include_router(room_router)
app.include_router(worker_router)
app.include_router(user_router)
