package common

import crypto.CryptoProvider
import crypto.PrivateKey
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import repositories.OutdatedData
import repositories.SpacesRepository
import repositories.initialValue


@OutdatedData
fun makeSpacesDataSynchronizer(savingScope: CoroutineScope, spacesRepository: SpacesRepository) = DataSynchronizer(
    coroutineScope = savingScope,
    originalFlow = spacesRepository.currentSpaces,
    savedFlow = spacesRepository.savedSpacesFlow.mapState(savingScope) { it ?: initialValue },
    updateOriginalFlow = spacesRepository::updateSpaces,
    forceResetEventsFlow = spacesRepository.resetEvents,
)

fun makeSpaceStructureSyncData(
    spacesData: DataSynchronizer<PersistentList<EncryptedSpaceInfo>>,
    spaceIndex: Int,
    privateKey: PrivateKey,
    cryptoProvider: CryptoProvider,
) = spacesData.map(
    transformForward = {
        runLogging {
            val space = it[spaceIndex]
            withContext(Dispatchers.Default) {
                SpaceStructure.fromEncryptedBytes(
                    cryptoProvider, space,
                    privateKey
                )
            }
        }
    },
    initialValue = null,
    transformBackward = {
        if (it == null) return@map this
        runLogging {
            val oldEncryptedSpace = get(spaceIndex)
            val encryptedSpace = withContext(Dispatchers.Default) {
                it.toEncryptedBytes(
                    oldEncryptedSpace.name,
                    privateKey,
                    cryptoProvider
                )
            }
            set(spaceIndex, encryptedSpace)
        } ?: this
    },
)
