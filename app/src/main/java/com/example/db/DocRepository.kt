package com.example.db

import kotlinx.coroutines.flow.Flow

class DocRepository(private val docDao: DocDao) {
    val allDocs: Flow<List<DocEntity>> = docDao.getAllDocs()

    suspend fun getDocById(id: Int): DocEntity? = docDao.getDocById(id)

    suspend fun insert(doc: DocEntity): Long = docDao.insertDoc(doc)

    suspend fun update(doc: DocEntity) = docDao.updateDoc(doc)

    suspend fun delete(doc: DocEntity) = docDao.deleteDoc(doc)
}
