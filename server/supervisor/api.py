from fastapi import FastAPI

from supervisor.routes.room import room_router
from supervisor.routes.worker import worker_router
from supervisor.routes.user import user_router

app = FastAPI()
app.include_router(room_router)
app.include_router(worker_router)
app.include_router(user_router)
