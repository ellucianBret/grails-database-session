package grails.plugin.databasesession

/**
 * @author Burt Beckwith
 */
class DatabaseCleanupService {

	def grailsApplication
	def persistentSessionService

	/**
	 * Delete PersistentSessions and corresponding PersistentSessionAttributes where
	 * the last accessed time is older than a cutoff value.
	 */
	void cleanup() {

		def conf = grailsApplication.config.grails.plugin.databasesession
		float maxAge = (conf.cleanup.maxAge ?: 30) as Float

        while(persistentSessionService.cleanChunkOfExpiredSessions(maxAge)) {}
	}
}
