package common

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


class PersistentListSerializer<T>(serializer: KSerializer<T>) : KSerializer<PersistentList<T>> {
    private val delegate = ListSerializer(serializer)

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = SerialDescriptor("PersistentList", delegate.descriptor)

    override fun deserialize(decoder: Decoder): PersistentList<T> {
        return decoder.decodeSerializableValue(delegate).toPersistentList()
    }

    override fun serialize(encoder: Encoder, value: PersistentList<T>) {
        encoder.encodeSerializableValue(delegate, value)
    }
}

class PersistentMapSerializer<K, V>(
    keySerializer: KSerializer<K>,
    valueSerializer: KSerializer<V>
) : KSerializer<PersistentMap<K, V>> {
    private val delegate = MapSerializer(keySerializer, valueSerializer)

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = SerialDescriptor("PersistentMap", delegate.descriptor)

    override fun deserialize(decoder: Decoder): PersistentMap<K, V> {
        return decoder.decodeSerializableValue(delegate).toPersistentMap()
    }

    override fun serialize(encoder: Encoder, value: PersistentMap<K, V>) {
        encoder.encodeSerializableValue(delegate, value)
    }
}