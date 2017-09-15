/**
 * Copyright (c) 2015, CodiLime Inc.
 */

package io.deepsense.experimentmanager.storage.cassandra

import com.datastax.driver.core.Session
import com.google.inject.name.Named
import com.google.inject.{AbstractModule, Provides, Singleton}

import io.deepsense.commons.cassandra.{ClusterFactory, SessionFactory}

class ExperimentSessionModule extends AbstractModule {

  override def configure(): Unit = {}

  @Provides
  @Singleton
  @Named("ExperimentsSession")
  def provideSession(
      @Named("cassandra.host") host: String,
      @Named("cassandra.port") port: Int,
      @Named("cassandra.credentials.user") user: String,
      @Named("cassandra.credentials.password") password: String,
      @Named("cassandra.experiments.keyspace") keySpace: String,
      @Named("cassandra.reconnect.delay") reconnectDelay: Long,
      clusterFactory: ClusterFactory,
      sessionFactory: SessionFactory): Session = {
    val cluster = clusterFactory.create(host, port, user, password, reconnectDelay)
    sessionFactory.connect(cluster, keySpace)
  }
}