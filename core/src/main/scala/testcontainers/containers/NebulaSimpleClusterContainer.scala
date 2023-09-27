package testcontainers.containers

import scala.jdk.OptionConverters._

import org.slf4j.{ Logger, LoggerFactory }
import org.testcontainers.containers.GenericContainer

/**
 * @author
 *   梦境迷离
 * @version 1.0,2023/9/18
 */
object NebulaSimpleClusterContainer {
  private val logger: Logger = LoggerFactory.getLogger(classOf[NebulaClusterContainer])
}

/**
 * The simplest Nebula service, with one storaged, one metad, and one graphd.
 *
 * @param version
 *   The image version/tag
 * @param absoluteHostPathPrefix
 *   The prefix of your host path, eg: prefix/data/meta1:/data/meta, prefix/logs/meta1:/logs
 */
class NebulaSimpleClusterContainer(
  version: String = Nebula.DefaultTag,
  absoluteHostPathPrefix: Option[String] = None,
  subnetIp: String = "172.28.0.0/16"
) extends NebulaClusterContainer(subnetIp) {

  import NebulaSimpleClusterContainer._

  def this(version: String, absoluteBindingPath: java.util.Optional[String]) =
    this(version, absoluteBindingPath.toScala)

  protected override val metaIpPortMapping: List[(String, Int)] = List(
    increaseLastIp(gatewayIp, 1) -> Nebula.MetadExposedPort
  )

  // Does the IP have to be self-incrementing?
  protected override val storageIpMapping: List[(String, Int)] = List(
    increaseLastIp(gatewayIp, 2) -> Nebula.StoragedExposedPort
  )

  protected override val graphIpMapping: List[(String, Int)] = List(
    increaseLastIp(gatewayIp, 3) -> Nebula.GraphdExposedPort
  )

  logger.info(s"Nebula meta nodes started at ip: ${generateIpAddrs(metaIpPortMapping)}")
  logger.info(s"Nebula storage nodes started at ip: ${generateIpAddrs(storageIpMapping)}")
  logger.info(s"Nebula graph nodes started at ip: ${generateIpAddrs(graphIpMapping)}")

  protected override val metads: List[GenericContainer[_]] =
    metaIpPortMapping.map { case (ip, port) =>
      new NebulaMetadContainer(
        version,
        ip,
        metaAddrs,
        NebulaMetadContainer.defaultPortBindings(port),
        absoluteHostPathPrefix.fold(List.empty[NebulaVolume])(p => NebulaMetadContainer.defaultVolumeBindings(p))
      )
    }.map(_.withNetwork(nebulaNet))

  protected override val storageds: List[GenericContainer[_]] =
    storageIpMapping.map { case (ip, port) =>
      new NebulaStoragedContainer(
        version,
        ip,
        metaAddrs,
        NebulaStoragedContainer.defaultPortBindings(port),
        absoluteHostPathPrefix.fold(List.empty[NebulaVolume])(p => NebulaStoragedContainer.defaultVolumeBindings(p))
      )
    }.map(_.dependsOn(metads: _*)).map(_.withNetwork(nebulaNet))

  protected override val graphds: List[GenericContainer[_]] = graphIpMapping.map { case (ip, port) =>
    new NebulaGraphdContainer(
      version,
      ip,
      metaAddrs,
      NebulaGraphdContainer.defaultPortBindings(port),
      absoluteHostPathPrefix.fold(List.empty[NebulaVolume])(p => NebulaGraphdContainer.defaultVolumeBindings(p))
    )
  }.map(_.dependsOn(metads: _*)).map(_.withNetwork(nebulaNet))

  protected override val console: NebulaConsoleContainer = new NebulaConsoleContainer(
    version,
    graphdIp = graphIpMapping.head._1,
    graphdPort = graphIpMapping.head._2,
    storagedAddrs = storageIpMapping
  ).withNetwork(nebulaNet)

  override def existsRunningContainer: Boolean =
    metads.exists(_.isRunning) || storageds.exists(_.isRunning) || graphds.exists(_.isRunning) || console.isRunning
}
