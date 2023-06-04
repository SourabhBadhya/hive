<?php
namespace metastore;

/**
 * Autogenerated by Thrift Compiler (0.16.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
use Thrift\Base\TBase;
use Thrift\Type\TType;
use Thrift\Type\TMessageType;
use Thrift\Exception\TException;
use Thrift\Exception\TProtocolException;
use Thrift\Protocol\TProtocol;
use Thrift\Protocol\TBinaryProtocolAccelerated;
use Thrift\Exception\TApplicationException;

final class CompactionType
{
    const MINOR = 1;

    const MAJOR = 2;

    const REBALANCE = 3;

    const ABORT_TXN_CLEANUP = 4;

    static public $__names = array(
        1 => 'MINOR',
        2 => 'MAJOR',
        3 => 'REBALANCE',
        4 => 'ABORT_TXN_CLEANUP',
    );
}

