from typing import Any, Dict, Union

from aiohttp import ClientSession, ContentTypeError
from pydantic.main import BaseModel

CLIENTS = {}


def get_client(host: str, port: int):
    url = f'{host}:{port}'
    if url in CLIENTS:
        return CLIENTS[url]
    else:
        client = SpeechlessWorkerClientAsync(host, port)
        CLIENTS[url] = client
        return client


class LowLevelAsyncClientMixin:
    def __init__(self, host: str, port: int, **kwargs):
        self.host = host
        self.port = port
        self._session = None

        super(LowLevelAsyncClientMixin, self).__init__(**kwargs)

    async def get_session(self):
        if self._session is None:
            self._session = ClientSession()
        return self._session

    async def _do_request(self, endpoint: str, method: str,
                          data: Union[BaseModel, str, None, Dict[str, Any]] = None,
                          timeout: int = 20) -> None:
        data = data.dict() if isinstance(data, BaseModel) else data
        url = f'http://{self.host}:{self.port}{endpoint}'
        session = await self.get_session()

        async with session.request(method, url, json=data, timeout=timeout) as resp:
            if resp.status >= 400:
                raise Exception("Code {:d} returned for {} {} endpoint".format(resp.status, method, endpoint))


class SpeechlessWorkerClientAsync(LowLevelAsyncClientMixin):

    async def ping(self):
        await self._do_request('/ping', 'GET')

    async def start_conference(self, room_id: int):
        await self._do_request('/start_conference', 'POST', data={'conference': int(room_id)})

    async def stop_conference(self, room_id: int):
        await self._do_request('/stop_conference', 'DELETE', data={'conference': int(room_id)})

    async def add_user(self, room_id: int, user_id: int):
        await self._do_request('/add_user', 'POST', data={'conference': int(room_id), 'user': int(user_id)})

    async def remove_user(self, room_id: int, user_id: int):
        await self._do_request('/remove_user', 'DELETE', data={'conference': int(room_id), 'user': int(user_id)})
