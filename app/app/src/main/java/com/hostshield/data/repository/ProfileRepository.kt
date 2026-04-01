package com.hostshield.data.repository

import com.hostshield.data.database.ProfileDao
import com.hostshield.data.model.BlockingProfile
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao
) {
    fun getAllProfiles(): Flow<List<BlockingProfile>> = profileDao.getAllProfiles()
    suspend fun getAllProfilesList(): List<BlockingProfile> = profileDao.getAllProfilesList()
    suspend fun getActiveProfile(): BlockingProfile? = profileDao.getActiveProfile()
    suspend fun addProfile(profile: BlockingProfile): Long = profileDao.insert(profile)
    suspend fun updateProfile(profile: BlockingProfile) = profileDao.update(profile)
    suspend fun deleteProfile(profile: BlockingProfile) = profileDao.delete(profile)
    suspend fun deactivateAllProfiles() = profileDao.deactivateAll()
    suspend fun activateProfile(id: Long) {
        profileDao.activateExclusive(id)
    }
}
