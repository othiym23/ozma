functor

import
   OzmaRuntime('NewObject':NewObject) at '../../scala/ozma/OzmaRuntime.ozf'
   `functor:java.lang.Object`('type:java.lang.Object':Object
                              'class:java.lang.Object':ObjectClass) at 'Object.ozf'
   `functor:java.lang.Class`('type:java.lang.Class':Class
                             'class:java.lang.Class':ClassClass) at 'Class.ozf'

export
   'type:java.lang.String':String
   'class:java.lang.String':StringClass

define

   class String from Object
      attr
         rawString

      meth '<init>'(ARawString $)
         Object, '<init>'(_)
         rawString := ARawString
         unit
      end

      meth toString($)
         self
      end

      meth toRawString($)
         @rawString
      end

      meth '$plus#1063877011'(Right $)
         Raw = (@rawString)#{Right toRawString($)}
      in
         {NewObject String StringClass '<init>'(Raw _)}
      end
   end

   StringClass = {ByNeed fun {$}
                            {NewObject Class ClassClass
                             '<init>'("java.lang.String"
                                      ObjectClass
                                      nil
                                      [ObjectClass]
                                      _)}
                         end}

end