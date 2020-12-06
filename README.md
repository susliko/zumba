# `Zumba` video-chat
## Zumba provides
- creating and joining conversation rooms
- capturing audio from any connected device
- capturing video from a webcam
- transferring audio and video to all room participants
- playing sound and displaying video of all participants

## How Zumba works
On startup the application registers itself on a `Supervisor` server, obtaining `userId`.
After creating/joining a room, it starts to transmit media Datagram packets (audio and video separately) to the `Rumba` server and to listen for incoming packets. Each packet is provided with header, containing `userId` and `roomId`. `Supervisor` manages rooms and `Rumba` workers, which dispatch packets between users. Video is ripped into tiles, compressed with JPEG to ensure low network bandwith usage. 
