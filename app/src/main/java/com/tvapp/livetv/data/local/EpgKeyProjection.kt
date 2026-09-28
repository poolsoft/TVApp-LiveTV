package com.tvapp.livetv.data.local

/** [ChannelDao.getEpgKeyColumns] projection: EPG anahtar adaylarını taşımak için
 *  tam varlık yüklemesi yerine yalnız üç kolon okur. */
data class EpgKeyColumns(
    val lastKnownName: String?,
    val customName: String?,
    val epgIdOverride: String?,
)

/** [IptvDao.getEpgKeyColumnsForEnabledChannels] projection: EPG anahtar adayları
 *  için yalnız üç kolon okur; streamUrl/logo gibi ağır kolonlar yüklenmez. */
data class IptvEpgKeyColumns(
    val displayName: String,
    val tvgId: String?,
    val tvgName: String?,
)
