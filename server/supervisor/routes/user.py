from typing import Dict

from fastapi import APIRouter, Depends

from supervisor.datas import User
from supervisor.db import RedisClient, get_db
from supervisor.utils import catch_exceptions

user_router = APIRouter()


@user_router.post('/user/create/', response_model=User)
@catch_exceptions
async def create_user(name: str, db: RedisClient = Depends(get_db)):
    id_to_user = await db.get_users()
    new_user = User(name=name, id=await db.get_users_free_id())
    id_to_user[new_user.id] = new_user
    await db.set_users(id_to_user)
    return new_user


@user_router.post('/user/remove/')
@catch_exceptions
async def remove_user(user_id: int, db: RedisClient = Depends(get_db)):
    id_to_user = await db.get_users()
    id_to_user.pop(user_id)
    await db.set_users(id_to_user)


@user_router.get('/user/all/', response_model=Dict[int, User])
@catch_exceptions
async def get_all_users(db: RedisClient = Depends(get_db)):
    users = await db.get_users()
    return users
