package dev.josueamaya.pourline.grinder

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un par de calibración (SPEC-006, enmienda 1): [valueA] en [grinderAId] muele igual que [valueB] en [grinderBId], en
 * unidades base. Siempre en el orden canónico `grinderAId < grinderBId`; los índices únicos detectan así un valor
 * repetido sin importar el orden en que lo introdujo el usuario. Borrar un molino borra sus pares (`CASCADE`).
 */
@Entity(
  tableName = "grind_calibrations",
  foreignKeys = [
    ForeignKey(entity = GrinderEntity::class, parentColumns = ["id"], childColumns = ["grinderAId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = GrinderEntity::class, parentColumns = ["id"], childColumns = ["grinderBId"], onDelete = ForeignKey.CASCADE),
  ],
  indices = [
    Index("grinderAId"),
    Index("grinderBId"),
    Index(value = ["grinderAId", "grinderBId", "valueA"], unique = true),
    Index(value = ["grinderAId", "grinderBId", "valueB"], unique = true),
  ],
)
data class GrindCalibrationEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val grinderAId: Long,
  val valueA: Double,
  val grinderBId: Long,
  val valueB: Double,
  val updatedAtEpochMs: Long,
)

fun GrindCalibrationEntity.toCalibration(): GrindCalibration = GrindCalibration(id, grinderAId, valueA, grinderBId, valueB)
