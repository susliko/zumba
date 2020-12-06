package ui.conrollers

import java.awt.image.BufferedImage

import ui._
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.layout.{BorderPane, GridPane}
import javafx.stage.Stage
import media.{AudioHeader, AudioSegment, ImageHeader, ImageSegment, Microphone, Playback, Webcam, ZumbaConfig}
import ui.conrollers.menu.MenuController
import ui.conrollers.room.RoomController
import web.{MediaClient, SupervisorClient, UByte}
import zio.blocking.Blocking
import zio.{Chunk, Fiber, RIO, Ref, Runtime, Task, TaskManaged, UIO, ZManaged}

import scala.util.Random

class Mediator(
                config: ZumbaConfig,
                primaryStage: Stage,
                imageClient: MediaClient[ImageSegment],
                audioClient: MediaClient[AudioSegment],
                supervisorClient: SupervisorClient,
                settingsRef: Ref[Settings],
                activeController: Ref[Option[Controller]],
                microphoneFiber: Ref[Option[Fiber[Throwable, Unit]]],
                playbackFiber: Ref[Option[Fiber[Throwable, Unit]]],
                selfVideoFiber: Ref[Option[Fiber[Throwable, Unit]]],
                imageSegmentsFiber: Ref[Option[Fiber[Throwable, Unit]]],
              )(implicit runtime: Runtime[Blocking]) {

  def shutdownFiber(ref: Ref[Option[Fiber[Throwable, Unit]]]): UIO[Unit] =
    for {
      maybeFiber <- ref.getAndSet(None)
      _ <- UIO.foreach(maybeFiber)(_.interruptFork)
    } yield ()

  def getSettings: Task[Settings] = settingsRef.get


  def setName(name: String): Task[Unit] =
    activeController.get.flatMap {
      case Some(Controller.Menu(menu)) =>
        settingsRef.update(_.copy(name = name))
      case Some(Controller.Room(room)) =>
        Task.unit
    }

  // ***** Room managment *****

  def createRoom: Task[Unit] =
    for {
      settings <- settingsRef.get
      userId <- supervisorClient.createUser(settings.name)
      roomWithId <- supervisorClient.createRoom(userId)
      _ <- settingsRef.update(_.copy(
        userId = userId,
        roomId = roomWithId.room_id,
        workerHost = roomWithId.worker_host,
        workerAudioPort = roomWithId.worker_audio_port,
        workerVideoPort = roomWithId.worker_video_port
      ))
      _ <- UIO(println(s"User id: $userId"))
      _ <- UIO(println(s"Room with id: $roomWithId"))
      _ <- switchScene(SceneType.Room)
    } yield ()

  def joinRoom(roomId: UByte): Task[Unit] =
    for {
      settings <- settingsRef.get
      userId <- supervisorClient.createUser(settings.name)
      room <- supervisorClient.joinRoom(roomId, userId)
      _ <- settingsRef.update(_.copy(
        userId = userId,
        roomId = roomId,
        workerHost = room.worker_host,
        workerAudioPort = room.worker_audio_port,
        workerVideoPort = room.worker_video_port
      ))
      _ <- UIO(println(s"User id: $userId"))
      _ <- UIO(println(s"Room: $room"))
      _ <- switchScene(SceneType.Room)
    } yield ()

  // ***** Microphone *****

  def enableMicrophone: RIO[Blocking, Unit] =
    activeController.get.flatMap {
      case Some(Controller.Room(room)) =>
        for {
          selectedMicrophone <- settingsRef.get.map(_.selectedMicrophone)
          // TODO: Send audio to server here
          fiber <- Microphone.managedByName(selectedMicrophone)
            .use(mic =>
              mic
                .stream(config.audioBufSize)
                .mapChunks(
                  chunk =>
                    Chunk(
                      AudioSegment(AudioHeader(config.roomId, config.userId), chunk)
                    )
                )
                .tap(c => UIO(println(s"Sending $c")).when(config.logPackets))
                .run(audioClient.sendSink(config.rumbaHost, config.rumbaAudioPort))
            )
            .onError(x => UIO(println(x.untraced.prettyPrint))).forkDaemon
          maybeOldMicrophoneFiber <- microphoneFiber.getAndSet(Some(fiber))
          _ <- Task.foreach(maybeOldMicrophoneFiber)(_.interrupt)
          _ <- settingsRef.update(_.copy(useMicrophone = true))
        } yield ()
      case _ => Task.unit
    }

  def selectMicrophone(name: String): RIO[Blocking, Unit] =
    for {
      _ <- shutdownFiber(microphoneFiber)
      settings <- settingsRef.updateAndGet(_.copy(selectedMicrophone = name))
      _ <- enableMicrophone.when(settings.useMicrophone)
    } yield ()

  def disableMicrophone: Task[Unit] =
    for {
      _ <- shutdownFiber(microphoneFiber)
      _ <- settingsRef.update(_.copy(useMicrophone = false))
    } yield ()

  // ***** Playback *****

  def enablePlayback: Task[Unit] =
    activeController.get.flatMap {
      case Some(Controller.Room(room)) =>
        for {
          selectedPlayback <- settingsRef.get.map(_.selectedPlayback)
          // TODO: Send audio to server here
          fiber <- Playback.managedByName(selectedPlayback).use(play =>
            audioClient
              .acceptStream(config.audioBufSize * 8)
              .tap(
                c =>
                  UIO(println(s"Got ${c.header} of size ${c.audio.size}"))
                    .when(config.logPackets)
              )
              .mapConcatChunk(_.audio)
              .run(play.sink)
          )
            .onError(x => UIO(println(x.untraced.prettyPrint))).forkDaemon
          maybeOldPlaybackFiber <- playbackFiber.getAndSet(Some(fiber))
          _ <- Task.foreach(maybeOldPlaybackFiber)(_.interrupt)
          _ <- settingsRef.update(_.copy(usePlayback = true))
        } yield ()
      case _ => Task.unit
    }

  def selectPlayback(name: String): Task[Unit] =
    for {
      _ <- shutdownFiber(playbackFiber)
      settings <- settingsRef.updateAndGet(_.copy(selectedPlayback = name))
      _ <- enablePlayback.when(settings.usePlayback)
    } yield ()

  def disablePlayback: Task[Unit] =
    for {
      _ <- shutdownFiber(playbackFiber)
      _ <- settingsRef.update(_.copy(usePlayback = false))
    } yield ()

  // ***** Webcam *****

  def enableWebcam: Task[Unit] =
    activeController.get.flatMap {
      case Some(Controller.Room(room)) =>
        for {
          selectedWebcam <- settingsRef.get.map(_.selectedWebcam)
          // TODO: Send video to server here
          fiber <- Webcam.managedByName(selectedWebcam).use(webcam =>
            webcam.stream.run(
              room.selfVideoSink
                .zipPar(imageClient.sendSink(config.rumbaHost, config.rumbaVideoPort).contramapChunks(images => images.flatMap(image => ImageSegment.fromImage(image, config.roomId, config.userId))))
            )
          ).unit.forkDaemon
          maybeOldSelfVideoFiber <- selfVideoFiber.getAndSet(Some(fiber))
          _ <- Task.foreach(maybeOldSelfVideoFiber)(_.interrupt)
          _ <- settingsRef.update(_.copy(useWebcam = true))
        } yield ()

      case None =>
        Task.unit
    }

  def disableWebcam: Task[Unit] =
    for {
      _ <- shutdownFiber(selfVideoFiber)
      _ <- settingsRef.update(_.copy(useWebcam = false))
    } yield ()

  def selectWebcam(name: String): Task[Unit] =
    for {
      _ <- shutdownFiber(selfVideoFiber)
      settings <- settingsRef.updateAndGet(_.copy(selectedWebcam = name))
      _ <- enableWebcam.when(settings.useWebcam)
    } yield ()

  // ***** Initialization / Finalization *****

  def kek(chunk: Chunk[BufferedImage]): Chunk[ImageSegment] =
    chunk
      .flatMap(ImageSegment.fromImage(_, 1, (Random.nextInt(2) + 1).toByte))
      .map(
        segment =>
          if (segment.header.userId == 2) {
            segment.copy(header = segment.header.copy(x = ((segment.header.x + 1) % 3).toByte))
          } else {
            segment
          }
      )

  def release: UIO[Unit] =
    for {
      _ <- shutdownFiber(selfVideoFiber)
      _ <- shutdownFiber(imageSegmentsFiber)
      _ <- shutdownFiber(imageSegmentsFiber)
      _ <- shutdownFiber(microphoneFiber)
      _ <- shutdownFiber(playbackFiber)
      settings <- settingsRef.get
      _ <- activeController.get.flatMap {
        case Some(Controller.Room(_)) =>
          supervisorClient.leaveRoom(settings.roomId, settings.userId).ignore *>
            supervisorClient.removeUser(settings.userId).ignore
        case _ => Task.unit
      }
    } yield ()

  def finalizeController(controller: Controller): Task[Unit] =
    controller match {
      case Controller.Menu(menu) =>
        Task.unit
      case Controller.Room(room) =>
        for {
          _ <- shutdownFiber(selfVideoFiber)
          _ <- shutdownFiber(imageSegmentsFiber)
          _ <- shutdownFiber(imageSegmentsFiber)
          _ <- shutdownFiber(microphoneFiber)
          _ <- shutdownFiber(playbackFiber)
          settings <- settingsRef.get
          _ <- (
            supervisorClient.leaveRoom(settings.roomId, settings.userId).ignore *>
              supervisorClient.removeUser(settings.userId).ignore
            ).forkDaemon
        } yield ()
    }

  def switchScene(sceneType: SceneType): Task[Unit] =
    activeController.get.flatMap(maybeController => Task.foreach(maybeController)(finalizeController)) *>
      (sceneType match {
        case SceneType.Menu =>
          val xmlUrl = getClass.getResource("/scenes/menu.fxml")
          val loader = new FXMLLoader
          loader.setLocation(xmlUrl)
          val menuController = new MenuController(this)
          loader.setController(menuController)
          val root: GridPane = loader.load
          (activeController.set(Some(Controller.Menu(menuController))) *>
            runOnFxThread { () =>
              primaryStage.setScene(new Scene(root))
              primaryStage.show()
              primaryStage.setWidth(600)
              primaryStage.setHeight(400)
            }).onError(x => UIO(println(x)))

        case SceneType.Room =>
          (for {
            roomController <- RoomController.apply(this)
            scene <- Task {
              val xmlUrl = getClass.getResource("/scenes/room.fxml")
              val loader = new FXMLLoader
              loader.setController(roomController)
              loader.setLocation(xmlUrl)
              val root: BorderPane = loader.load
              new Scene(root)
            }
            _ <- runOnFxThread { () =>
              primaryStage.setScene(scene)
              primaryStage.show()
            }
            _ <- activeController.set(Some(Controller.Room(roomController)))
            _ <- audioClient.ack(config.rumbaHost, config.rumbaAudioPort, AudioHeader(1, 1).toBytes).forkDaemon
            _ <- imageClient.ack(config.rumbaHost, config.rumbaVideoPort, ImageHeader(config.roomId, config.userId, 1, 1).toBytes).forkDaemon
            _ <- roomController.start
            fiber <- imageClient
              .acceptStream(config.videoBufSize * 10)
              .run(roomController.imageSegmentsSink)
              .interruptible
              .forkDaemon
            maybeOldFiber <- imageSegmentsFiber.getAndSet(Some(fiber))
            _ <- Task.foreach(maybeOldFiber)(_.interrupt)
          } yield ()).onError(x => UIO(println(x.prettyPrint)))
      })
}

object Mediator {

  def acquireMediator(
                       config: ZumbaConfig,
                       primaryStage: Stage,
                       imageClient: MediaClient[ImageSegment],
                       audioClient: MediaClient[AudioSegment],
                       supervisorClient: SupervisorClient
                     )(implicit runtime: Runtime[Blocking]): Task[Mediator] =
    for {
      microphoneNames <- Microphone.names()
      playbackNames <- Playback.names()
      videoNames <- Webcam.names
      inputOptions <- Ref.make(
        Settings(
          "Это я",
          useMicrophone = true,
          usePlayback = true,
          useWebcam = true,
          microphoneNames.head,
          playbackNames.head,
          videoNames.head,
          userId = new UByte(0),
          roomId = new UByte(0),
          workerHost = config.rumbaHost,
          workerVideoPort = config.rumbaVideoPort,
          config.rumbaAudioPort
        ))
      activeController <- Ref.make[Option[Controller]](None)
      microphoneFiber <- Ref.make[Option[Fiber[Throwable, Unit]]](None)
      playbackFiber <- Ref.make[Option[Fiber[Throwable, Unit]]](None)
      selfVideoFiber <- Ref.make[Option[Fiber[Throwable, Unit]]](None)
      videoSegmentsFiber <- Ref.make[Option[Fiber[Throwable, Unit]]](None)
    } yield new Mediator(config, primaryStage, imageClient, audioClient, supervisorClient, inputOptions, activeController, microphoneFiber, playbackFiber, selfVideoFiber, videoSegmentsFiber)

  def apply(config: ZumbaConfig, primaryStage: Stage)(implicit runtime: Runtime[Blocking]): TaskManaged[Mediator] =
    for {
      imageClient <- MediaClient.managed[ImageSegment](config.localVideoPort)
      audioClient <- MediaClient.managed[AudioSegment](config.localAudioPort)
      supervisorClient <- SupervisorClient.managed(config.supervisorUrl)
      mediator <- ZManaged.make(acquireMediator(config, primaryStage, imageClient, audioClient, supervisorClient))(_.release)
    } yield mediator
}
