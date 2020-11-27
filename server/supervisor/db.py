import json
from typing import Dict, List, Optional

from aioredis import Redis, create_redis

from supervisor.config import config
from supervisor.datas import Room, Worker

INIT_WORKERS = [Worker(id=0, url='')]


class RedisClient:
    def __init__(self):
        self.redis: Optional[Redis] = None

    async def setup(self) -> None:
        self.redis = await create_redis(config.REDIS_DSN, encoding='utf-8')
        await self.set_workers(INIT_WORKERS)

    async def finalize(self) -> None:
        self.redis.close()

    async def get_rooms(self) -> Dict[str, Room]:
        id_to_room_raw = await self.redis.get('rooms')
        id_to_room = json.loads(id_to_room_raw if id_to_room_raw is not None else '{}')
        return {id: Room.parse_raw(room) for id, room in id_to_room.items()}

    async def set_rooms(self, id_to_room: Dict[str, Room]) -> None:
        await self.redis.set('rooms', json.dumps({id: room.json() for id, room in id_to_room.items()}))

    async def get_workers(self) -> List[Worker]:
        workers_raw = await self.redis.get('workers')
        workers = json.loads(workers_raw if workers_raw is not None else '[]')
        return [Worker.parse_raw(worker) for worker in workers]

    async def set_workers(self, workers: List[Worker]) -> None:
        await self.redis.set('workers', json.dumps([worker.json() for worker in workers]))
