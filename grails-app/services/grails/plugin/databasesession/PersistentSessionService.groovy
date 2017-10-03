package grails.plugin.databasesession

import org.springframework.transaction.annotation.Transactional
import org.springframework.util.Assert

/**
 * @author Burt Beckwith
 */
class PersistentSessionService {
    def grailsApplication


	def deserializeAttributeValue(byte[] serialized) {
		if (!serialized) {
			return null
		}

		// might throw IOException - let the caller handle it
		new ObjectInputStream(new ByteArrayInputStream(serialized)) {
			@Override
			protected Class<?> resolveClass(ObjectStreamClass objectStreamClass) throws IOException, ClassNotFoundException {
				Class.forName objectStreamClass.name, true, Thread.currentThread().contextClassLoader
			}
		}.readObject()
	}

	byte[] serializeAttributeValue(value) {
		if (value == null) {
			return null
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream()
		// might throw IOException - let the caller handle it
		new ObjectOutputStream(baos).writeObject value
		baos.toByteArray()
	}

	PersistentSessionAttributeValue findValueBySessionAndAttributeName(PersistentSession session, String name) {
		PersistentSession.executeQuery(
				'from PersistentSessionAttributeValue v ' +
				'where v.attribute.session=:session and v.attribute.name=:name',
				[session: session, name: name])[0]
	}

	List<PersistentSessionAttributeValue> findValuesBySession(String sessionId) {
		Assert.hasLength sessionId

		PersistentSession.executeQuery(
			'from PersistentSessionAttributeValue v where v.attribute.session.id=:sessionId order by v.id',
			[sessionId: sessionId])
	}

	@Transactional
	void deleteValuesBySessionId(String sessionId) {
		Assert.hasLength sessionId

		deleteValuesByIds(PersistentSession.executeQuery(
			'select id from PersistentSessionAttributeValue v ' +
			'where v.attribute.session.id=:sessionId',
			[sessionId: sessionId]))
	}

	@Transactional
	void deleteValuesBySessionIds(sessionIds) {
		Assert.notEmpty sessionIds

		deleteValuesByIds(PersistentSession.executeQuery(
			'select id from PersistentSessionAttributeValue v ' +
			'where v.attribute.session.id in (:sessionIds)',
			[sessionIds: sessionIds]))
	}

	@Transactional
	void removeValue(String sessionId, String name) {
		Assert.hasLength sessionId
		Assert.hasLength name

		deleteValuesByIds(PersistentSession.executeQuery(
			'select id from PersistentSessionAttributeValue v ' +
			'where v.attribute.session.id=:sessionId and v.attribute.name=:name',
			[sessionId: sessionId, name: name]))
	}

	protected void deleteValuesByIds(ids) {
		if (!ids) {
			return
		}

		PersistentSession.executeUpdate(
			'delete from PersistentSessionAttributeValue v where v.id in (:ids)',
			[ids: ids])
	}

	@Transactional
	void deleteAttributesBySessionId(String sessionId) {
		PersistentSession.executeUpdate(
			'delete from PersistentSessionAttribute a where a.session.id=:sessionId',
			[sessionId: sessionId])
	}

	@Transactional
	void deleteAttributesBySessionIds(sessionIds) {
		PersistentSession.executeUpdate(
			'delete from PersistentSessionAttribute a where a.session.id in (:sessionIds)',
			[sessionIds: sessionIds])
	}

	@Transactional
	void removeAttribute(String sessionId, String name) {
		PersistentSession.executeUpdate(
			'delete from PersistentSessionAttribute psa ' +
			'where psa.session.id=:sessionId and psa.name=:name',
			[sessionId: sessionId, name: name])
	}

	List<String> findAllAttributeNames(String sessionId) {
		PersistentSession.executeQuery(
			'select psa.name from PersistentSessionAttribute psa ' +
			'where psa.session.id=:sessionId',
			[sessionId: sessionId])
	}

	List<String> findAllSessionIdsByLastAccessedOlderThan(long age) {
		PersistentSession.executeQuery(
			'select s.id from PersistentSession s where s.lastAccessedTime < :age',
			[age: age])
	}

	@Transactional
	void deleteSessionsByIds(ids) {
		PersistentSession.executeUpdate(
			'delete from PersistentSession s where s.id in (:ids)',
			[ids: ids])
	}

	Boolean isSessionInvalidated(String sessionId) {
		PersistentSession.executeQuery(
			'select s.invalidated from PersistentSession s where s.id=:id',
			[id: sessionId])[0]
	}

	Boolean cleanChunkOfExpiredSessions(maxAgeMinutes) {
		long expiredTime = System.currentTimeMillis() - maxAgeMinutes * 1000 * 60

		def maxSessionCleanSize = grailsApplication.config.grails.plugin.databasesession.cleanup.maxSessionCleanSize ?: 1000
		// remove at most X sessions at a time that are beyond the expiredTime of lastAccess to limit the transaction size
        def sessionIds = PersistentSession.executeQuery(
            'select s.id from PersistentSession s where s.lastAccessedTime < :age',
            [age: expiredTime], [max: maxSessionCleanSize])
        if (sessionIds.size > 0) {
            if (log.isDebugEnabled()) {
                log.debug "using max age $maxAgeMinutes, found old sessions to remove count: $sessionIds.size"
            }
            def attributeIds = PersistentSessionAttribute.executeQuery(
                    'select a.id from PersistentSessionAttribute a where  a.session.id in (:sessionIds)',
                    [sessionIds: sessionIds])
            PersistentSessionAttributeValue.executeUpdate(
                    'delete from PersistentSessionAttributeValue v where v.attribute.id in (:attributeIds)',
                    [attributeIds: attributeIds])
            PersistentSessionAttribute.executeUpdate(
                    'delete from PersistentSessionAttribute a where a.id in (:attributeIds)',
                    [attributeIds: attributeIds])
            PersistentSession.executeUpdate(
                    'delete from PersistentSession s where s.id in (:sessionIds)',
                    [sessionIds: sessionIds])
        }

		return sessionIds.size == maxSessionCleanSize
	}
}
