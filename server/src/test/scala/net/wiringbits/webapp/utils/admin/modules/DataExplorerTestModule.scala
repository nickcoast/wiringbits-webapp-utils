package net.wiringbits.webapp.utils.admin.modules

import com.google.inject.{AbstractModule, Provides}
import net.wiringbits.webapp.utils.admin.config.{DataExplorerSettings, TableSettings}
import net.wiringbits.webapp.utils.admin.utils.models.ordering.OrderingCondition

class DataExplorerTestModule extends AbstractModule {

  @Provides()
  def dataExplorerSettings: DataExplorerSettings = {
    DataExplorerSettings(settings)
  }

  val settings: List[TableSettings] = List(
    TableSettings("users", OrderingCondition("created_at DESC, user_id"), "user_id")
  )
}