package grails.plugin.databasesession

/**
 * @author Burt Beckwith
 */
class DatabaseCleanupJob {

	def databaseCleanupService
	def grailsApplication

	static String TRIGGER_NAME = "DatabaseSessionTrigger"

	static triggers = {
		simple name: TRIGGER_NAME, startDelay: 1000 * 10, repeatInterval: 1000 * 60 * 10
	}


	void execute() {
		def conf = grailsApplication.config.grails.plugin.databasesession
		if (conf.cleanup.enabled instanceof Boolean && !conf.cleanup.enabled) {
			return
		}

		databaseCleanupService.cleanup()
	}
}
