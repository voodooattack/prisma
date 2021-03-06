package com.prisma.deploy.connector.mongo

import com.prisma.config.DatabaseConfig
import com.prisma.deploy.connector._
import com.prisma.deploy.connector.mongo.impl.{CloudSecretPersistenceImpl, MigrationPersistenceImpl, MongoDeployMutactionExecutor, ProjectPersistenceImpl}
import com.prisma.shared.models.{Project, ProjectIdEncoder}
import org.joda.time.DateTime

import scala.concurrent.{ExecutionContext, Future}

case class MongoDeployConnector(config: DatabaseConfig)(implicit ec: ExecutionContext) extends DeployConnector {
  lazy val internalDatabaseDefs = MongoInternalDatabaseDefs(config)
  lazy val mongoClient          = internalDatabaseDefs.client
  lazy val internalDatabase     = mongoClient.getDatabase("prisma")

  override val isActive: Boolean = true

  override val migrationPersistence: MigrationPersistence       = MigrationPersistenceImpl(internalDatabase)
  override val projectPersistence: ProjectPersistence           = ProjectPersistenceImpl(internalDatabase, migrationPersistence)
  override val deployMutactionExecutor: DeployMutactionExecutor = MongoDeployMutactionExecutor(mongoClient)
  override val projectIdEncoder: ProjectIdEncoder               = ProjectIdEncoder('_')
  override val cloudSecretPersistence: CloudSecretPersistence   = CloudSecretPersistenceImpl(internalDatabase)
  override def capabilities                                     = Set.empty

  override def clientDBQueries(project: Project): ClientDbQueries                              = EmptyClientDbQueries
  override def databaseIntrospectionInferrer(projectId: String): DatabaseIntrospectionInferrer = EmptyDatabaseIntrospectionInferrer

  override def initialize(): Future[Unit] = Future.unit

  override def reset(): Future[Unit] = {
    for {
      collectionNames <- internalDatabase.listCollectionNames().toFuture()
      collections     = collectionNames.map(internalDatabase.getCollection)
      _               <- Future.sequence(collections.map(_.drop.toFuture))
    } yield ()
  }

  override def shutdown(): Future[Unit] = Future.unit

  override def createProjectDatabase(id: String): Future[Unit] = { // This is a hack
    mongoClient.getDatabase(id).listCollectionNames().toFuture().map(_ -> Unit)
  }

  override def deleteProjectDatabase(id: String): Future[Unit] = {
    mongoClient.getDatabase(id).drop().toFuture().map(_ -> Unit)
  }

  override def getAllDatabaseSizes(): Future[Vector[DatabaseSize]] = Future.successful(Vector.empty)

  override def getOrCreateTelemetryInfo(): Future[TelemetryInfo] = Future.successful(TelemetryInfo("not Implemented", None))

  override def updateTelemetryInfo(lastPinged: DateTime): Future[Unit] = Future.unit
}
