package com.somecompany.examples.exceptions;

import lombok.experimental.StandardException;

@StandardException
public sealed class DomainException extends RuntimeException permits NotFoundException {
}
