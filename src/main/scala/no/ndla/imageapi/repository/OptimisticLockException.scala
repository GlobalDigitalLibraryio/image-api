package no.ndla.imageapi.repository

class OptimisticLockException(message: String = "The resource is outdated. Please try fetching before submitting again.") extends RuntimeException(message)
